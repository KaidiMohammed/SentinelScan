---
phase: 02-secrets-exposes-dans-les-bundles
plan: 01
subsystem: worker-checks
tags: [secrets, redaction, supabase, stripe, openai, postgres, flyway, java]

requires:
  - phase: 01-socle-des-scans
    provides: "Check interface, job queue, evidence storage, scans table (V1). NOT AVAILABLE YET; see Phase 1 dependency gap"
provides:
  - "BundleSecretsCheck: passive detector for known secrets in a page + same-origin JS bundles, redacted findings only"
  - "Redactor: deterministic masking + sha256 fingerprint"
  - "V2__findings.sql: findings table with no plaintext column and anti-plaintext CHECKs"
  - "test-targets/leaky-bundle: FAKE service_role key target (vulnerable / fixed)"
affects: [01-socle-des-scans, 04-rapport-et-note]

tech-stack:
  added: []
  patterns:
    - "Findings carry only type, source URL, line, redacted evidence, fingerprint; the plaintext never leaves the check"
    - "Check result is FINDINGS / NO_ISSUE / NOT_VERIFIED(+reason), never a fake 'no issue'"

key-files:
  created:
    - worker/src/main/java/com/sentinelscan/checks/secrets/BundleSecretsCheck.java
    - worker/src/main/java/com/sentinelscan/checks/Redactor.java
    - api/src/main/resources/db/migration/V2__findings.sql
    - test-targets/leaky-bundle/index.html
  modified: []

key-decisions:
  - "Supabase JWTs are decoded in memory; only role=service_role is reported (anon keys are public by design)"
  - "Redaction: secrets < 16 chars fully masked; otherwise type prefix + fixed **** + last 4, with visible chars capped at 1/3"
  - "Fingerprint = sha256: + 16 hex chars, unsalted so re-scans compare (high-entropy keys only)"
  - "Only same-origin scripts/modulepreload are fetched; private/loopback hosts refused by default (best-effort SSRF guard)"
  - "findings.scan_id has no FK until Phase 1 creates the scans table"

patterns-established:
  - "Plain JDK-only check API (run(URI), scanContent(source, content)) pending wiring to the Phase 1 Check interface"

requirements-completed: []

duration: ~10min
completed: 2026-10-25
---

# Phase 2 Plan 01: Exposed secrets in JS bundles Summary

**JDK-only passive detector for Supabase service_role / sb_secret, Stripe live, OpenAI, Anthropic, GitHub, AWS and private-key secrets in a page and its same-origin JS bundles. It always returns redacted evidence plus a SHA-256 fingerprint, and a FK-free `findings` migration has no column that can hold plaintext.**

## Performance

- **Duration:** about 10 min
- **Started:** about 2026-10-25T13:53Z
- **Completed:** 2026-10-25T14:03Z
- **Tasks:** 4/4
- **Files created:** 4

## Accomplishments
- `BundleSecretsCheck` does passive GETs only (8 MB per resource, 40 bundles, 15 s timeout). It scans the HTML page, including inline scripts, plus same-origin `<script src>` and `<link rel=modulepreload|preload>` JS, and decodes Supabase JWT payloads so only service_role is reported. Results are `FINDINGS`, `NO_ISSUE` or `NOT_VERIFIED` with a reason. The class logs nothing.
- `Redactor` provides deterministic masking (for example `eyJ****ests`, `sk_live_****1234`) and a `sha256:<16 hex>` fingerprint for before/after comparison.
- `V2__findings.sql` stores only `evidence_redacted` and `fingerprint`. CHECK constraints require the `****` mask and reject evidence that matches a full JWT, `sk_live_`, `sk-proj-`, `sb_secret_`, GitHub, AWS or PRIVATE KEY pattern.
- The `leaky-bundle` test target has a FAKE service_role key between `// LEAK START` and `// LEAK END`, plus a FAKE anon key that stays as a negative control.

## Task Commits

1. **Task 2: Redactor.java**: `265afce` (feat), committed first because Task 1 depends on it
2. **Task 1: BundleSecretsCheck.java**: `db2acfc` (feat)
3. **Task 3: V2__findings.sql**: `92fc7f4` (feat)
4. **Task 4: test-targets/leaky-bundle/index.html**: `8410952` (test)

## Files Created/Modified
- `worker/src/main/java/com/sentinelscan/checks/secrets/BundleSecretsCheck.java`: detector, fetcher, pattern catalogue
- `worker/src/main/java/com/sentinelscan/checks/Redactor.java`: masking and fingerprint
- `api/src/main/resources/db/migration/V2__findings.sql`: findings table (redacted only)
- `test-targets/leaky-bundle/index.html`: vulnerable test target with FAKE keys

## Verification (what actually ran)

The plan's `<verify>` is `make test` for every task. **There is no Makefile and no Maven/Gradle build (that scaffolding belongs to Phase 1), so `make test` was NOT run and did not pass.** What did run:

1. **Compilation:** `javac -Xlint:all` (OpenJDK 21.0.12 Temurin) on both worker classes, output in `/tmp/sscan-build`. It compiled with 0 warnings after a serialVersionUID fix.
2. **Throwaway harness** (`/tmp/sscan-harness/Harness.java`, not committed), run as `java -cp /tmp/sscan-build Harness test-targets/leaky-bundle`. Result: **21 passed, 0 failed**. It covers:
   - Redactor: short secrets fully masked, deterministic output, mask present, fingerprint format, anon and service_role fingerprints differ.
   - Offline scan of `index.html`: exactly 1 finding, `supabase_service_role_jwt`, fingerprint matches, anon key ignored.
   - Same file with the LEAK block removed: 0 findings.
   - **Over HTTP** (JDK `SimpleFileServer` on 127.0.0.1): the leaky target returns `FINDINGS` with evidence `eyJ****ests (JWT role=service_role, ref=fakeprojectref00000000)` (criterion 1). Re-scanning the fixed copy returns `NO_ISSUE` (criterion 2, at check level).
   - Same-origin `app.js` with fake `sk_live_` and `sk-proj-` keys: both found and redacted. A cross-origin script was not fetched; a modulepreload chunk was fetched.
   - With the default constructor, a loopback target returns `NOT_VERIFIED` ("non-public address refused"). An unreachable port returns `NOT_VERIFIED`, not `NO_ISSUE`.
   - Synthetic sb_secret, legacy OpenAI, Anthropic, GitHub and AWS strings each give one typed finding. `sk-test-…` and `pk_live_…` are not reported, and the Anthropic key is not double-reported as OpenAI.
   - The string form of every returned object contains no plaintext secret, no JWT payload segment and no signature.
3. **SQL migration:** **not executed**. There is no Postgres, psql or Docker in this environment. Its syntax and CHECK regexes are unverified against a real database.

## Phase 1 dependency gap (important)

The ROADMAP says Phase 2 depends on Phase 1, and Phase 1 is not implemented: there is no worker/api build, `Check` interface, job queue, `scans` table (V1), evidence storage or logging setup. As a result:
- `BundleSecretsCheck` exposes a plain API and must be wired to the Phase 1 `Check` interface and job runner.
- `findings.scan_id` has no foreign key. Add `REFERENCES scans(id)` once V1 exists, and align the type if it isn't UUID.
- **Success criterion 3 (no plaintext in the DB or logs) is only partly shown.** The check returns no plaintext and logs nothing, and the schema has no plaintext column. The end-to-end path (worker logs, HTTP client debug logs, persistence) can't be checked until Phase 1 exists.
- Criteria 1 and 2 are shown **at the check level** against the real test target served over HTTP, not through a full scan (front → job → worker → DB). For that reason SEC-01/02/03 are **not** marked complete in REQUIREMENTS.md.

## Deviations from Plan

1. **[Ordering] Task 2 committed before Task 1.** `BundleSecretsCheck` imports `Redactor`, so committing it first keeps every commit compilable.
2. **[Rule 2 - Security] Best-effort SSRF guard.** The check scans user-supplied URLs, so by default it refuses loopback, private, link-local, CGNAT and ULA addresses, including redirect targets. A constructor flag allows local test targets. It does not protect against DNS rebinding; real egress isolation must come from the Phase 1 worker sandbox.
3. **[Rule 2 - Correctness] NOT_VERIFIED status with reasons**, so an unreachable target is never reported as "no issue" (brief §6, CORE-01).
4. **[Rule 2 - Security] Anti-plaintext CHECK constraints** in V2, beyond a comment.
5. **Flyway version overlap:** the team board flagged that another phase may also use V2, and an admin accepted it. Renumber at merge time if they collide.

## Known Stubs / Limits
- No stubs. v1 limits: chunks loaded dynamically at runtime (for example Next.js/webpack lazy chunks not listed in the HTML) are not followed. Source maps are out of scope (SRC-01, v0.2).
- Redacted evidence keeps the last 4 characters and the type prefix. JWT evidence also exposes the Supabase project ref and role, which are not secret.

## Threat Flags

| Flag | File | Description |
|------|------|-------------|
| threat_flag: outbound-fetch | worker/.../BundleSecretsCheck.java | Fetches user-supplied URLs (SSRF surface). Mitigated best-effort in code; worker network isolation needed (Phase 1) |

## Open Issues
- Wire the check to the Phase 1 `Check` interface and persistence. Add the `scans` FK. Run the migration on a real Postgres.
- Add real unit tests under the Phase 1 build (the /tmp harness can be ported) so that `make test` exists and means something.
- Run an end-to-end SEC-03 proof (scan → re-scan via the app) and audit worker logs for plaintext once Phase 1 is in place.

## Self-Check: PASSED
- All 4 files exist; commits 265afce, db2acfc, 92fc7f4, 8410952 exist on feature/SENT-2-02.
