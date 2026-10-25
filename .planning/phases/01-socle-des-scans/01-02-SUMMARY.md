---
phase: 01-socle-des-scans
plan: 02
subsystem: scan-progress
tags: [sse, spring-boot, nextjs, eventsource]
requires: [01-01]
provides:
  - "GET /api/scans/{id}/events (SSE, poll-and-diff on Postgres)"
  - "/scan/[id] live progress page"
  - "launch form on home page"
key-files:
  created:
    - api/src/main/java/com/sentinelscan/scan/ScanProgressStream.java
    - api/src/main/java/com/sentinelscan/scan/ScanCheckRow.java
    - api/src/test/java/com/sentinelscan/scan/ScanProgressStreamTest.java
    - web/app/scan/[id]/page.tsx
    - web/next.config.mjs
  modified:
    - api/src/main/java/com/sentinelscan/scan/ScanJobRepository.java
    - web/app/page.tsx
decisions:
  - "SSE emits a 'progress' event only when the DB state really changed; closes on done/failed"
  - "A done job with zero checks renders an explicit 'no check was run' message, never an all-clear"
  - "Web proxies /api to the API via next.config rewrites (compress disabled for SSE) instead of CORS"
metrics:
  tasks: 2
  completed: 2026-10-25
---

# Phase 1 Plan 02: Live progress and true per-check status

SSE endpoint streaming real job and `scan_check` rows, plus a French progress page and a launch form. No result is simulated.

## Tasks and commits

| Task | Commit |
|------|--------|
| 2 ScanProgressStream.java (+ ScanCheckRow, test) | 1341936 |
| fix: `findChecks` in ScanJobRepository (missed from the previous commit, which was incomplete on its own) | see git log (`fix(01-02)`) |
| 1 page.tsx (+ home form, next.config.mjs) | 9786adf |

## Behavior

- Statuses shown in French: running "En cours…", finding "Problème trouvé", clean "Aucun problème trouvé", not_checked "n'a pas pu être vérifié" plus the reason.
- Phase 1 registers no checks, so a finished job shows an empty check list and "Aucune vérification n'a été exécutée (note)", stating it is not an all-clear.
- Failed job shows the stored error, or says no reason was given.

## Verification: NOT EXECUTED

- **Java:** not compiled, tests not run (Maven Central returns 403). `ScanProgressStreamTest` was written but never run; compile errors are possible.
- **Web:** `npm install` failed once (npm registry 403) and was not retried. No `tsc`, no `next build`, no browser test. The TypeScript is unchecked (notably the Next 16 / React 19 `useParams` typing).
- **SSE behavior** (streaming through the Next rewrite without buffering, close handling, EventSource reconnect after the server closes) is untested against a real server or Postgres. Without `.close()` handling the browser would auto-reconnect; the page closes the source on done/failed to avoid that.
- `make test` was not run (no make); the Makefile already covers api and worker, and no web test target was added because there are no web tests.

## Deviations from Plan

- [Rule 3] Plan tasks were terse; added `ScanCheckRow`, `findChecks`, the home form, and `next.config.mjs` as derived from the objective and notes.
- Commit 1341936 omitted the `ScanJobRepository` change because a python edit failed (python3 absent); fixed in a follow-up commit. The intermediate commit therefore would not compile on its own.

## Known Stubs

None. The home page text states that no checks exist yet.

## Self-Check: PASSED (files and commits exist; behavior unverified, see above)
