package com.sentinelscan.checks;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Deterministic masking of secrets (SEC-02).
 *
 * <p>Rules:
 * <ul>
 *   <li>A secret is never returned, stored or logged in clear: callers only keep
 *       {@link #redact(String, int)} output and {@link #fingerprint(String)}.</li>
 *   <li>Secrets shorter than {@value #MIN_PARTIAL_LENGTH} chars are fully masked.</li>
 *   <li>Longer secrets keep a short identifying prefix (e.g. {@code sk_live_}) and the
 *       last {@value #SUFFIX_LENGTH} chars; everything else becomes a fixed-width mask
 *       {@value #MASK}, so the output does not reveal the secret length.</li>
 *   <li>Visible characters never exceed a third of the secret.</li>
 * </ul>
 *
 * <p>The output always contains {@value #MASK}; the {@code findings} table enforces this
 * with a CHECK constraint (see {@code V2__findings.sql}).
 */
public final class Redactor {

    public static final String MASK = "****";
    static final String FULL_MASK = "********";
    static final int MIN_PARTIAL_LENGTH = 16;
    static final int SUFFIX_LENGTH = 4;
    static final int DEFAULT_PREFIX_LENGTH = 4;
    static final int MAX_PREFIX_LENGTH = 12;
    /** Hex chars kept from the SHA-256 digest (64 bits): enough to compare re-scans. */
    static final int FINGERPRINT_HEX_LENGTH = 16;

    private Redactor() {
    }

    /** Redacts with the default visible prefix length. */
    public static String redact(String secret) {
        return redact(secret, DEFAULT_PREFIX_LENGTH);
    }

    /**
     * Masks {@code secret}, keeping at most {@code prefixLength} leading chars and the last
     * {@value #SUFFIX_LENGTH} chars, capped so that visible chars stay below a third of the
     * secret. Deterministic: same input, same output.
     */
    public static String redact(String secret, int prefixLength) {
        if (secret == null || secret.length() < MIN_PARTIAL_LENGTH) {
            return FULL_MASK;
        }
        int len = secret.length();
        int maxVisible = len / 3;
        int suffix = Math.min(SUFFIX_LENGTH, maxVisible / 2);
        int prefix = Math.max(0, Math.min(Math.min(prefixLength, MAX_PREFIX_LENGTH), maxVisible - suffix));
        return secret.substring(0, prefix) + MASK + secret.substring(len - suffix);
    }

    /**
     * Non-reversible fingerprint used to recognise the same secret across re-scans
     * (before/after comparison) without keeping it: {@code sha256:} + first
     * {@value #FINGERPRINT_HEX_LENGTH} hex chars of SHA-256(secret).
     *
     * <p>Unsalted on purpose so that the same secret matches across scans; the secrets
     * targeted here are high-entropy keys, so the truncated digest cannot be brute-forced
     * back to the key.
     */
    public static String fingerprint(String secret) {
        if (secret == null) {
            throw new IllegalArgumentException("secret must not be null");
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(secret.getBytes(StandardCharsets.UTF_8));
            return "sha256:" + HexFormat.of().formatHex(digest).substring(0, FINGERPRINT_HEX_LENGTH);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is mandatory on every Java platform.
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
