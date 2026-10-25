package com.sentinelscan.checks.secrets;

import com.sentinelscan.checks.Redactor;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Passive check (SEC-01): downloads the public page of a target and its same-origin JS
 * bundles, then looks for known secrets.
 *
 * <p>Guarantees (SEC-02):
 * <ul>
 *   <li>Only passive HTTP GETs, with size, count and time limits.</li>
 *   <li>A matched secret never leaves this class in clear: {@link Finding} only holds the
 *       type, source URL, line, redacted evidence and a non-reversible fingerprint. Nothing
 *       is logged here.</li>
 *   <li>Supabase JWTs are decoded in memory and flagged only when {@code role=service_role};
 *       anon keys are public by design and are not findings.</li>
 *   <li>If the page cannot be fetched, the result is {@link Status#NOT_VERIFIED} with the
 *       reason, never a fake "no issue".</li>
 * </ul>
 *
 * <p>Phase 1 dependency: the worker's {@code Check} interface, job queue and evidence storage
 * do not exist yet. This class exposes a plain API ({@link #run(URI)},
 * {@link #scanContent(String, String)}) and must be wired to the Phase 1 interface once it lands.
 * Network egress isolation (SSRF) is also a Phase 1 worker concern; this class adds a
 * best-effort guard against private/loopback addresses (see {@link #BundleSecretsCheck(Limits, boolean)}).
 *
 * <p>Known v1 limits: only {@code <script src>} and {@code <link rel=modulepreload|preload href>}
 * referenced by the HTML page are fetched (plus inline scripts in the page); chunks loaded
 * dynamically at runtime are not followed.
 */
public final class BundleSecretsCheck {

    public static final String CHECK_ID = "bundle_secrets";

    public enum Status { FINDINGS, NO_ISSUE, NOT_VERIFIED }

    /**
     * A secret finding. Contains no plaintext secret by construction.
     *
     * @param type             catalogue identifier, e.g. {@code supabase_service_role_jwt}
     * @param severity         {@code critical}, {@code high} or {@code medium}
     * @param sourceUrl        URL of the page or bundle where it was found
     * @param line             1-based line in that resource
     * @param redactedEvidence masked value plus non-secret context (never the secret)
     * @param fingerprint      {@code sha256:<16 hex>} for re-scan comparison
     */
    public record Finding(String type, String severity, String sourceUrl, int line,
                          String redactedEvidence, String fingerprint) {
    }

    /**
     * @param status      overall status of the check
     * @param findings    findings (empty unless {@link Status#FINDINGS})
     * @param scanned     URLs actually scanned
     * @param unverified  resources that could not be scanned, with the reason
     */
    public record Result(Status status, List<Finding> findings, List<String> scanned,
                         List<String> unverified) {
        public Result {
            findings = List.copyOf(findings);
            scanned = List.copyOf(scanned);
            unverified = List.copyOf(unverified);
        }
    }

    /** Fetch limits. */
    public record Limits(int maxBundles, long maxBytesPerResource, Duration timeout) {
        public static final Limits DEFAULT = new Limits(40, 8L * 1024 * 1024, Duration.ofSeconds(15));
    }

    /** Catalogue entry: pattern + how much prefix may stay visible once redacted. */
    private record SecretPattern(String type, String severity, Pattern pattern, int visiblePrefix,
                                 boolean supabaseJwt) {
    }

    // Boundaries: a key must not be glued to other key-like characters.
    private static final String B = "(?<![A-Za-z0-9_\\-])";
    private static final String E = "(?![A-Za-z0-9_\\-])";

    private static final List<SecretPattern> CATALOGUE = List.of(
            // Supabase legacy JWT keys (anon / service_role). Only service_role is reported.
            new SecretPattern("supabase_service_role_jwt", "critical",
                    Pattern.compile(B + "eyJ[A-Za-z0-9_\\-]{10,}\\.eyJ[A-Za-z0-9_\\-]{10,}\\.[A-Za-z0-9_\\-]{10,}" + E),
                    3, true),
            // Supabase new-format secret keys (publishable keys sb_publishable_ are public).
            new SecretPattern("supabase_secret_key", "critical",
                    Pattern.compile(B + "sb_secret_[A-Za-z0-9_\\-]{20,}" + E), 10, false),
            new SecretPattern("stripe_live_secret_key", "critical",
                    Pattern.compile(B + "sk_live_[0-9A-Za-z]{20,}" + E), 8, false),
            new SecretPattern("stripe_live_restricted_key", "high",
                    Pattern.compile(B + "rk_live_[0-9A-Za-z]{20,}" + E), 8, false),
            new SecretPattern("openai_api_key", "critical",
                    Pattern.compile(B + "sk-(?:proj|svcacct|admin)-[A-Za-z0-9_\\-]{20,}" + E), 8, false),
            new SecretPattern("openai_api_key_legacy", "critical",
                    Pattern.compile(B + "sk-[A-Za-z0-9]{20}T3BlbkFJ[A-Za-z0-9]{20}" + E), 3, false),
            new SecretPattern("anthropic_api_key", "critical",
                    Pattern.compile(B + "sk-ant-(?:api|admin)\\d{2}-[A-Za-z0-9_\\-]{40,}" + E), 7, false),
            new SecretPattern("github_token", "high",
                    Pattern.compile(B + "(?:gh[pousr]_[A-Za-z0-9]{36,}|github_pat_[A-Za-z0-9_]{60,})" + E), 4, false),
            new SecretPattern("aws_access_key_id", "high",
                    Pattern.compile(B + "(?:AKIA|ASIA)[0-9A-Z]{16}" + E), 4, false),
            new SecretPattern("private_key_block", "critical",
                    Pattern.compile("-----BEGIN (?:RSA |EC |DSA |OPENSSH |PGP )?PRIVATE KEY(?: BLOCK)?-----"
                            + "[\\s\\S]{20,8192}?-----END (?:RSA |EC |DSA |OPENSSH |PGP )?PRIVATE KEY(?: BLOCK)?-----"),
                    0, false)
    );

    private static final Pattern SCRIPT_SRC = Pattern.compile(
            "<script\\b[^>]*?\\bsrc\\s*=\\s*([\"']?)([^\"'\\s>]+)\\1", Pattern.CASE_INSENSITIVE);
    private static final Pattern LINK_TAG = Pattern.compile("<link\\b[^>]*>", Pattern.CASE_INSENSITIVE);
    private static final Pattern LINK_REL_JS = Pattern.compile(
            "\\brel\\s*=\\s*[\"']?(?:modulepreload|preload)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern LINK_HREF = Pattern.compile(
            "\\bhref\\s*=\\s*([\"']?)([^\"'\\s>]+)\\1", Pattern.CASE_INSENSITIVE);
    private static final Pattern ROLE_CLAIM = Pattern.compile("\"role\"\\s*:\\s*\"([^\"]{1,64})\"");
    private static final Pattern REF_CLAIM = Pattern.compile("\"ref\"\\s*:\\s*\"([a-z0-9]{1,64})\"");

    private final Limits limits;
    private final boolean allowPrivateAddresses;
    private final HttpClient http;

    /** Production defaults: private/loopback targets refused. */
    public BundleSecretsCheck() {
        this(Limits.DEFAULT, false);
    }

    /**
     * @param allowPrivateAddresses {@code true} only for local test targets (e.g. 127.0.0.1)
     */
    public BundleSecretsCheck(Limits limits, boolean allowPrivateAddresses) {
        this.limits = Objects.requireNonNull(limits);
        this.allowPrivateAddresses = allowPrivateAddresses;
        this.http = HttpClient.newBuilder()
                .connectTimeout(limits.timeout())
                // NORMAL never follows https -> http; cross-origin bundles are filtered below.
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    /** Fetches the page at {@code target} and its same-origin JS, then scans everything. */
    public Result run(URI target) {
        List<String> scanned = new ArrayList<>();
        List<String> unverified = new ArrayList<>();
        List<Finding> findings = new ArrayList<>();

        String page;
        try {
            page = fetch(target);
        } catch (FetchException e) {
            unverified.add(target + " : " + e.getMessage());
            return new Result(Status.NOT_VERIFIED, List.of(), scanned, unverified);
        }
        scanned.add(target.toString());
        // Inline <script> blocks are part of the public JS: scan the whole document.
        findings.addAll(scanContent(target.toString(), page));

        int count = 0;
        for (URI bundle : extractBundleUris(target, page)) {
            if (count++ >= limits.maxBundles()) {
                unverified.add(bundle + " : bundle limit reached (" + limits.maxBundles() + ")");
                continue;
            }
            try {
                String js = fetch(bundle);
                scanned.add(bundle.toString());
                findings.addAll(scanContent(bundle.toString(), js));
            } catch (FetchException e) {
                unverified.add(bundle + " : " + e.getMessage());
            }
        }

        List<Finding> unique = dedupe(findings);
        Status status = unique.isEmpty() ? Status.NO_ISSUE : Status.FINDINGS;
        return new Result(status, unique, scanned, unverified);
    }

    /**
     * Scans already-downloaded content. Public so the detector can be exercised without
     * network. Returns redacted findings only.
     */
    public List<Finding> scanContent(String sourceUrl, String content) {
        List<Finding> out = new ArrayList<>();
        if (content == null || content.isEmpty()) {
            return out;
        }
        for (SecretPattern sp : CATALOGUE) {
            Matcher m = sp.pattern().matcher(content);
            while (m.find()) {
                String secret = m.group();
                String context = "";
                if (sp.supabaseJwt()) {
                    String payload = decodeJwtPayload(secret);
                    if (payload == null) {
                        continue;
                    }
                    Matcher role = ROLE_CLAIM.matcher(payload);
                    if (!role.find() || !"service_role".equals(role.group(1))) {
                        continue; // anon / authenticated keys are public by design
                    }
                    Matcher ref = REF_CLAIM.matcher(payload);
                    context = " (JWT role=service_role" + (ref.find() ? ", ref=" + ref.group(1) : "") + ")";
                }
                String evidence = Redactor.redact(secret, sp.visiblePrefix()) + context;
                out.add(new Finding(sp.type(), sp.severity(), sourceUrl, lineOf(content, m.start()),
                        evidence, Redactor.fingerprint(secret)));
            }
        }
        return out;
    }

    // ---------------------------------------------------------------------------------

    private static List<Finding> dedupe(List<Finding> findings) {
        Set<String> seen = new HashSet<>();
        List<Finding> out = new ArrayList<>();
        for (Finding f : findings) {
            if (seen.add(f.sourceUrl() + "|" + f.fingerprint())) {
                out.add(f);
            }
        }
        return out;
    }

    private static int lineOf(String content, int offset) {
        int line = 1;
        for (int i = 0; i < offset; i++) {
            if (content.charAt(i) == '\n') {
                line++;
            }
        }
        return line;
    }

    /** Decodes a JWT payload in memory; returns null if not decodable. */
    private static String decodeJwtPayload(String jwt) {
        String[] parts = jwt.split("\\.");
        if (parts.length != 3) {
            return null;
        }
        try {
            return new String(Base64.getUrlDecoder().decode(stripPadding(parts[1])), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String stripPadding(String s) {
        int end = s.length();
        while (end > 0 && s.charAt(end - 1) == '=') {
            end--;
        }
        return s.substring(0, end);
    }

    /** Same-origin script / modulepreload URLs referenced by the page, in order, deduplicated. */
    static Set<URI> extractBundleUris(URI page, String html) {
        Set<URI> out = new LinkedHashSet<>();
        Matcher s = SCRIPT_SRC.matcher(html);
        while (s.find()) {
            addIfSameOrigin(out, page, s.group(2));
        }
        Matcher l = LINK_TAG.matcher(html);
        while (l.find()) {
            String tag = l.group();
            if (LINK_REL_JS.matcher(tag).find()) {
                Matcher href = LINK_HREF.matcher(tag);
                if (href.find() && href.group(2).toLowerCase(Locale.ROOT).contains(".js")) {
                    addIfSameOrigin(out, page, href.group(2));
                }
            }
        }
        return out;
    }

    private static void addIfSameOrigin(Set<URI> out, URI page, String ref) {
        try {
            URI resolved = page.resolve(ref.replace("&amp;", "&")).normalize();
            if (sameOrigin(page, resolved)) {
                out.add(resolved);
            }
        } catch (IllegalArgumentException ignored) {
            // malformed reference: skip
        }
    }

    private static boolean sameOrigin(URI a, URI b) {
        return a.getScheme() != null && a.getScheme().equalsIgnoreCase(b.getScheme())
                && a.getHost() != null && a.getHost().equalsIgnoreCase(b.getHost())
                && effectivePort(a) == effectivePort(b);
    }

    private static int effectivePort(URI u) {
        if (u.getPort() != -1) {
            return u.getPort();
        }
        return "https".equalsIgnoreCase(u.getScheme()) ? 443 : 80;
    }

    private String fetch(URI uri) throws FetchException {
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!scheme.equals("http") && !scheme.equals("https")) {
            throw new FetchException("unsupported scheme");
        }
        if (!allowPrivateAddresses) {
            checkPublicHost(uri.getHost());
        }
        HttpRequest req = HttpRequest.newBuilder(uri)
                .GET()
                .timeout(limits.timeout())
                .header("User-Agent", "SentinelScan/0.1 (passive bundle secrets check)")
                .build();
        HttpResponse<InputStream> resp;
        try {
            resp = http.send(req, HttpResponse.BodyHandlers.ofInputStream());
        } catch (IOException e) {
            throw new FetchException("network error: " + e.getClass().getSimpleName());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new FetchException("interrupted");
        }
        if (!allowPrivateAddresses && !sameOriginOrPublicRedirect(uri, resp.uri())) {
            closeQuietly(resp.body());
            throw new FetchException("redirected to a non-public host");
        }
        if (resp.statusCode() != 200) {
            closeQuietly(resp.body());
            throw new FetchException("HTTP " + resp.statusCode());
        }
        try (InputStream in = resp.body()) {
            byte[] body = in.readNBytes((int) Math.min(Integer.MAX_VALUE - 8, limits.maxBytesPerResource() + 1));
            if (body.length > limits.maxBytesPerResource()) {
                throw new FetchException("larger than " + limits.maxBytesPerResource() + " bytes");
            }
            return new String(body, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new FetchException("read error: " + e.getClass().getSimpleName());
        }
    }

    private boolean sameOriginOrPublicRedirect(URI requested, URI finalUri) {
        if (sameOrigin(requested, finalUri)) {
            return true;
        }
        try {
            checkPublicHost(finalUri.getHost());
            return true;
        } catch (FetchException e) {
            return false;
        }
    }

    /**
     * Best-effort SSRF guard. Does not protect against DNS rebinding between this check and
     * the connection: real egress isolation belongs to the Phase 1 worker sandbox.
     */
    private static void checkPublicHost(String host) throws FetchException {
        if (host == null || host.isEmpty()) {
            throw new FetchException("missing host");
        }
        InetAddress[] addrs;
        try {
            addrs = InetAddress.getAllByName(host);
        } catch (UnknownHostException e) {
            throw new FetchException("unknown host");
        }
        for (InetAddress a : addrs) {
            byte[] b = a.getAddress();
            boolean cgnat = b.length == 4 && (b[0] & 0xff) == 100 && (b[1] & 0xc0) == 64;
            boolean ula = b.length == 16 && (b[0] & 0xfe) == 0xfc;
            if (a.isLoopbackAddress() || a.isAnyLocalAddress() || a.isLinkLocalAddress()
                    || a.isSiteLocalAddress() || a.isMulticastAddress() || cgnat || ula) {
                throw new FetchException("non-public address refused");
            }
        }
    }

    private static void closeQuietly(InputStream in) {
        try {
            in.close();
        } catch (IOException ignored) {
            // nothing to do
        }
    }

    /** Fetch failure; message is safe to store (never contains response content). */
    private static final class FetchException extends Exception {
        private static final long serialVersionUID = 1L;

        FetchException(String message) {
            super(message, null, false, false);
        }
    }
}
