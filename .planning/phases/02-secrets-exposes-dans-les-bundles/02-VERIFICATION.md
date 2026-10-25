---
phase: 02-secrets-exposes-dans-les-bundles
verified: 2026-10-25T15:00:00Z
status: gaps_found
score: 1/4 must-haves verified (3 partial, end-to-end blocked by Phase 1)
overrides_applied: 0
gaps:
  - truth: "SC1: Une clé Supabase service_role placée dans le bundle d'une cible de test est détectée avec sa preuve masquée"
    status: partial
    reason: "Detection and masking proven at check level (independent re-run over HTTP). But no code path runs the check from a scan or stores/displays its result: BundleSecretsCheck has zero callers and nothing inserts into findings. Root cause is that Phase 1 (job queue, worker, Check interface, scans table) does not exist."
    artifacts:
      - path: "worker/src/main/java/com/sentinelscan/checks/secrets/BundleSecretsCheck.java"
        issue: "ORPHANED: not wired to any worker/job runner (no Check interface exists)"
      - path: "api/src/main/resources/db/migration/V2__findings.sql"
        issue: "ORPHANED: no writer; no FK to scans; never executed on Postgres"
    missing:
      - "Phase 1: Check interface, job queue, worker, scans table (V1)"
      - "Wire BundleSecretsCheck into the worker, persist Result.findings into findings, add FK scan_id -> scans(id)"
      - "End-to-end proof: scan leaky-bundle from the front -> job -> worker -> DB"
  - truth: "SC2: Après retrait de la clé, le re-scan ne rapporte plus rien"
    status: partial
    reason: "Verified at check level (fixed copy -> NO_ISSUE). End-to-end re-scan is blocked by Phase 1. Separately, a real bug: if the page loads but every bundle fails (404, timeout, size limit, bundle cap), run() returns NO_ISSUE. A 'nothing reported' re-scan can therefore be a false negative, which contradicts the class Javadoc ('never a fake no issue') and the PROJECT truth constraint."
    artifacts:
      - path: "worker/src/main/java/com/sentinelscan/checks/secrets/BundleSecretsCheck.java"
        issue: "run() L193-195: status = findings.isEmpty() ? NO_ISSUE : FINDINGS, ignoring a non-empty unverified list"
    missing:
      - "Return NOT_VERIFIED (or a partial status carrying the reasons) when no findings and any bundle in unverified; never NO_ISSUE when the scan was incomplete"
      - "Unit test: page 200 + bundle 404 -> not NO_ISSUE"
  - truth: "SC3: Aucun secret en clair en base ni dans les logs"
    status: partial
    reason: "Redacted evidence and fingerprint are sound, the class logs nothing, and the schema has no plaintext column. But (a) there is no persistence or logging code, so 'in the DB and logs' can't be verified (blocked by Phase 1); (b) a real leak path: a secret in a bundle URL query string (e.g. <script src=/app.js?key=sk_live_...>) comes back in clear in Result.scanned and Finding.sourceUrl, and findings.source_url has no anti-plaintext CHECK."
    artifacts:
      - path: "worker/src/main/java/com/sentinelscan/checks/secrets/BundleSecretsCheck.java"
        issue: "scanned/unverified/sourceUrl store raw URLs (query strings included)"
      - path: "api/src/main/resources/db/migration/V2__findings.sql"
        issue: "anti-plaintext CHECKs cover evidence_redacted only, not source_url"
    missing:
      - "Strip the query string and fragment (or run the catalogue + redact) on URLs before they go into scanned/unverified/sourceUrl"
      - "Apply the same no-plaintext CHECK to findings.source_url"
      - "After Phase 1: audit worker + HttpClient logs during a leaky-bundle scan"
  - truth: "Best-effort SSRF guard refuses private targets including redirect targets (claimed in SUMMARY)"
    status: partial
    reason: "HttpClient is built with Redirect.NORMAL, so the JDK follows redirects inside http.send(). sameOriginOrPublicRedirect() checks only resp.uri() after the request has already gone out. A public page that 302s to http://169.254.169.254/... has its GET sent before the refusal (blind SSRF). Intermediate hops are never checked, so public -> private -> public is accepted."
    artifacts:
      - path: "worker/src/main/java/com/sentinelscan/checks/secrets/BundleSecretsCheck.java"
        issue: "L157 followRedirects(NORMAL) + L342 post-hoc check"
    missing:
      - "Use Redirect.NEVER and follow redirects manually (max N hops), calling checkPublicHost on every hop before sending"
      - "Phase 1 worker egress isolation (still required for DNS rebinding)"
  - truth: "SEC-01: bundles of the public URL are downloaded and scanned"
    status: partial
    reason: "Bundle URIs are resolved and same-origin-filtered against the URL the user typed, not the final URL after redirects. When the site redirects to a canonical host (apex -> www, http -> https) and uses absolute script URLs, every bundle is treated as cross-origin and skipped, so the result is NO_ISSUE with the key still exposed (reproduced)."
    artifacts:
      - path: "worker/src/main/java/com/sentinelscan/checks/secrets/BundleSecretsCheck.java"
        issue: "run() L179 extractBundleUris(target, page) uses target instead of the final response URI"
    missing:
      - "Have fetch() return the final URI and resolve/compare bundles against it"
deferred:
  - truth: "Masked proof is displayed to the user (the 'affiche' part of the phase goal)"
    addressed_in: "Phase 4"
    evidence: "Phase 4 SC2: 'Chaque carte donne explication, impact, preuve masquée et correctif'"
---

# Phase 2: Secrets exposés dans les bundles Verification Report

**Phase Goal:** Le scan passif trouve les secrets connus dans les bundles JS publics et les affiche toujours masqués, preuve à l'appui.
**Verified:** 2026-10-25T15:00:00Z
**Status:** gaps_found
**Re-verification:** No (initial verification)

## Goal Achievement

The detector and redactor are real and mostly correct. My independent re-run passed 38 of 41 checks. The phase goal is not met end to end, though: no scan invokes the check, nothing persists or displays its findings, and Phase 1, which Phase 2 depends on, is not implemented. Four code bugs (false NO_ISSUE, URL plaintext leak, post-hoc redirect SSRF guard, canonical-redirect detection miss) need fixing whatever happens with Phase 1.

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | SC1: service_role key in a test-target bundle is detected with masked proof | PARTIAL (check level verified; end-to-end blocked by Phase 1) | Independent HTTP run: `FINDINGS`, 1 finding `supabase_service_role_jwt`, evidence `eyJ****ests (JWT role=service_role, ref=fakeprojectref00000000)`, fingerprint `sha256:12afc12b305535d2`. Anon key not reported. No caller and no persistence. |
| 2 | SC2: after the key is removed, the re-scan reports nothing | PARTIAL (check level verified; end-to-end blocked by Phase 1; false-NO_ISSUE bug) | Fixed copy -> `NO_ISSUE`, 0 findings. Bug: page 200 + bundle 404 -> `NO_ISSUE` (reproduced). |
| 3 | SC3: no plaintext secret in the DB or logs | PARTIAL (blocked by Phase 1 + URL leak bug) | Result/Finding `toString()` contains no JWT, header, payload or signature segment. A secret in a script URL query appears in clear in `scanned` (reproduced). `source_url` has no CHECK. No DB writer or logging exists to audit. |
| 4 | PLAN: secrets in public JS bundles detected, always masked | VERIFIED (check level, with the SEC-01 caveats below) | Script src + modulepreload fetched, cross-origin skipped. 8 catalogue types each detected once and masked. Negatives (pk_live, sk_test, sb_publishable, anon JWT) not reported. Redactor visible chars <= len/3 for every length from 16 to 299. |

**Score:** 1/4 verified, 3 partial

### Deferred Items

| # | Item | Addressed In | Evidence |
|---|------|-------------|----------|
| 1 | UI display of the masked proof | Phase 4 | SC2 "preuve masquée" on finding cards |

Note: the Phase 1 dependency is not "deferred". Phase 1 comes before Phase 2 and is a prerequisite that has not been delivered.

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `worker/.../checks/Redactor.java` | masking + fingerprint | VERIFIED | Deterministic. <16 chars fully masked. `sha256:` + 16 hex. Used by BundleSecretsCheck. |
| `worker/.../checks/secrets/BundleSecretsCheck.java` | passive detector | ORPHANED | Substantive (414 lines), compiles cleanly with `-Xlint:all` on JDK 21. No callers anywhere in the repo. |
| `api/.../db/migration/V2__findings.sql` | findings table, redacted only | ORPHANED / not executed | Syntax reads as valid PG13+ (`gen_random_uuid()` is core only from PG13; earlier versions need pgcrypto). Not run against Postgres (none available). No writer. No FK. |
| `test-targets/leaky-bundle/index.html` | vulnerable target with FAKE keys | VERIFIED | FAKE service_role between LEAK markers + FAKE anon control. Removing the block gives the fixed case. |

### Key Link Verification

| From | To | Via | Status | Details |
|------|----|-----|--------|---------|
| BundleSecretsCheck | Redactor | `Redactor.redact/fingerprint` | WIRED | L224-226 |
| worker job runner | BundleSecretsCheck | Phase 1 `Check` interface | NOT_WIRED | No worker, no interface, no build (no Makefile/pom/gradle) |
| BundleSecretsCheck.Result | `findings` table | persistence | NOT_WIRED | No insert code anywhere |
| front (`web/app/page.tsx`) | scan API | form submit | NOT_WIRED | Form disabled. Shows "Pas encore implémenté" |

### Data-Flow Trace (Level 4)

| Artifact | Data Variable | Source | Produces Real Data | Status |
|----------|---------------|--------|--------------------|--------|
| findings table | rows | none | No | DISCONNECTED (Phase 1) |
| BundleSecretsCheck.run | page/bundles | real HTTP GET | Yes (shown over loopback) | FLOWING at check level |

### Behavioral Spot-Checks

Independent compile into `/tmp/verify02.pPDp/build` (outside the repo) and my own harness `/tmp/verify02.pPDp/src/V.java`. I did not reuse the executor's harness. Result: 38 pass, 3 fail.

| Behavior | Result | Status |
|----------|--------|--------|
| Offline scan of index.html: 1 service_role finding, anon ignored, fingerprint matches | as expected | PASS |
| Fixed copy (LEAK block removed): 0 findings | as expected | PASS |
| HTTP leaky -> FINDINGS; HTTP fixed -> NO_ISSUE | as expected | PASS |
| No JWT / header / payload / signature in Result string form | none found | PASS |
| Default ctor refuses 127.0.0.1, 169.254.169.254, `::ffff:127.0.0.1`, `0x7f000001` | NOT_VERIFIED | PASS |
| Unreachable target -> NOT_VERIFIED | `network error: ConnectException` | PASS |
| Same-origin script + modulepreload with sk_live / sk-proj keys, cross-origin skipped | both found, masked | PASS |
| Catalogue: sb_secret, rk_live, legacy OpenAI, Anthropic, ghp_, github_pat_, AKIA, PEM | each found once, masked | PASS |
| Page OK, only bundle 404 | `NO_ISSUE` | FAIL |
| Apex redirects to canonical host, page uses absolute bundle URL with sk_live | `NO_ISSUE`, bundle never fetched | FAIL |
| sk_live in a `<script src>` query string | plaintext in `scanned` / bundle `sourceUrl` | FAIL |
| Redactor output vs V2 CHECK regexes (java.util.regex approximation) | all real outputs accepted; raw secrets rejected | PASS |

### Probe Execution

No probes declared, and no `scripts/*/tests/probe-*.sh` exist. The plan's only verify command, `make test`, does not exist: there is no Makefile or build. SKIPPED.

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|-------------|------------|-------------|--------|----------|
| SEC-01 | ROADMAP (plan frontmatter `requirements: []`) | download public JS bundles, detect known secrets | PARTIAL | Works at check level. Misses bundles after a canonical-host redirect. Not run by any scan. |
| SEC-02 | ROADMAP | never stored or shown in clear; only fingerprint + masked excerpt | PARTIAL | Evidence/fingerprint OK. URL query leak. No persistence to verify. |
| SEC-03 | ROADMAP | proven on a test target: detected, then nothing after the fix | PARTIAL | Proven at check level only, not through the app. |

ORPHANED in the plan: the 02-01 PLAN declares `requirements: []` although ROADMAP maps SEC-01..03 to this phase. Its must_haves are one vague truth that restates none of the 3 SCs.

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| BundleSecretsCheck.java | 193-195 | NO_ISSUE despite non-empty `unverified` | WARNING | False "no problem" (violates the truth constraint) |
| BundleSecretsCheck.java | 157, 342 | redirect checked after it was followed | WARNING | Blind SSRF via redirect; intermediate hops unchecked |
| BundleSecretsCheck.java | 179 | bundles resolved against the pre-redirect URL | WARNING | Detection miss on canonical-host redirects |
| BundleSecretsCheck.java | 186, 225 | raw URLs stored | WARNING | Plaintext secret in sourceUrl/scanned if the URL carries one |
| BundleSecretsCheck.java | 98-99 | `(?<![A-Za-z0-9_\-])` boundary | INFO | Misses keys right after escaped text: `"eyJ…`, `%22eyJ…`, `\nsk_live…`, `Bearer%20sk_live…` (reproduced: 0 findings) |
| BundleSecretsCheck.java | 130, 288 | `\bsrc` also matches `data-src`; `.contains(".js")` also matches `.json` | INFO | Extra fetches only |
| all 4 files | - | TBD/FIXME/XXX/TODO | none | No debt markers |

### Human Verification Required

None that can run until Phase 1 exists. Once it does: an end-to-end leaky-bundle scan and re-scan through the app, plus a log audit, both covered by the gaps above.

### Gaps Summary

Root cause A (blocked by Phase 1): the check and migration are orphaned. No worker, job queue, Check interface, scans table, persistence or UI exists, so SC1-SC3 can only be shown at check level. The executor says this openly and did not mark SEC-01..03 complete. That is accurate.

Root cause B (Phase 2 code bugs, fixable now):
1. False `NO_ISSUE` when bundles could not be fetched.
2. Secrets in URLs leak in clear into `scanned`/`sourceUrl`, and `source_url` has no CHECK.
3. The SSRF redirect guard is post-hoc (`Redirect.NORMAL`), so the summary claim "refused including redirect targets" is inaccurate: the request is still sent.
4. Bundles are resolved against the pre-redirect URL, so detection misses canonical-host redirects.

Plan hygiene: `make test` never existed, so no task's automated verify ran. The plan omits SEC-01..03. The SQL has not been executed on a real Postgres.

---

_Verified: 2026-10-25T15:00:00Z_
_Verifier: Claude (gsd-verifier)_
