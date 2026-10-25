---
phase: 01-socle-des-scans
verified: 2026-10-25T00:00:00Z
status: gaps_found
score: 2/6 must-haves verified in code (none executed)
gaps:
  - truth: "CORE-03: Chaque constat stocke sa preuve brute (requête, extrait de réponse ou sortie d'outil), secrets masqués"
    status: failed
    reason: "No evidence column/table, no masking code anywhere. scan_check has only check_key/status/reason. Goal text says each check reports 'un statut vrai et une preuve'."
    artifacts:
      - path: "api/src/main/resources/db/migration/V1__scan_jobs.sql"
        issue: "No evidence/proof storage"
    missing:
      - "Evidence storage (request, response excerpt or tool output) linked to a finding"
      - "Secret masking before persistence"
  - truth: "CORE-04: Journal de qui a scanné quoi et quand"
    status: failed
    reason: "No audit log table or code. scan_job has no user/actor column; only created_at. grep for audit/evidence/proof/mask in api, worker, web returns nothing."
    artifacts:
      - path: "api/src/main/resources/db/migration/V1__scan_jobs.sql"
        issue: "No audit table, no actor"
    missing:
      - "Audit log (who, which target, when) written at scan creation"
  - truth: "A check can report a status (running/finding/clean/not_checked + reason) from the worker"
    status: failed
    reason: "Nothing writes scan_check. ScanCheck.run returns void and has no way to record a status. JdbcScanJobStore has no check-write method. ScanJobRunner never catches a per-check exception to record not_checked; a throwing check marks the whole job failed. Success criterion 3 (not_checked with reason) is only readable/renderable, not producible."
    artifacts:
      - path: "worker/src/main/java/com/sentinelscan/worker/ScanJobRunner.java"
        issue: "No per-check status write; no not_checked on failure"
      - path: "worker/src/main/java/com/sentinelscan/worker/ScanCheck.java"
        issue: "Interface returns void, no status/evidence channel"
    missing:
      - "Store methods to upsert scan_check rows (running -> terminal)"
      - "Per-check try/catch writing not_checked with the exception reason"
human_verification:
  - test: "Run `make test` (api and worker) with network access"
    expected: "Code compiles and unit tests pass; nothing has been compiled or run so far"
    why_human: "Maven Central blocked (403), no make"
  - test: "docker compose up; POST /api/scans from the front form"
    expected: "scan_job row created as queued, worker container claims it (status running then done), no port exposed on worker"
    why_human: "Needs Docker and Postgres; V1 SQL, SKIP LOCKED claim and compose never validated"
  - test: "Open /scan/{id} during a scan through the Next.js proxy"
    expected: "SSE progress events arrive live (not buffered); queued, running, done transitions are shown; empty-check job shows the explicit 'nothing was checked' message"
    why_human: "Runtime and SSE streaming behaviour"
  - test: "Run two workers against several queued jobs"
    expected: "Each job claimed exactly once (SKIP LOCKED)"
    why_human: "Concurrency needs real Postgres"
---

# Phase 1: Socle des scans, Verification Report

**Phase Goal:** Un scan lancé sur une URL devient un job exécuté par un worker isolé, dont chaque vérification remonte en direct un statut vrai et une preuve.
**Status:** gaps_found. Re-verification: No. All findings come from reading code. Nothing was compiled or run, so no test is counted as passing.

## Success Criteria

| # | Criterion | Status | Evidence |
|---|-----------|--------|----------|
| 1 | Scan from front creates a Postgres job taken by the Docker worker | CODE PRESENT, UNEXECUTED | `web/app/page.tsx` POSTs `/api/scans`; `ScanController.create` calls `ScanJobRepository.enqueue` (INSERT into scan_job); `JdbcScanJobStore.claimNext` uses FOR UPDATE SKIP LOCKED inside @Transactional; `docker-compose.yml` has a separate `worker` service with no ports. Needs runtime check. |
| 2 | Progress shows each check really running, no fake spinner | PARTIAL | `ScanProgressStream` does poll-and-diff on real DB rows; page renders only rows that exist. But nothing ever writes `scan_check`, so no check can appear as running. The mechanism is wired on the read side only. |
| 3 | Failed check shows "n'a pas pu être vérifié" with reason | PARTIAL (FAILED on producing side) | UI label and reason rendering exist and the DB constraint requires a reason. No code path in the worker produces `not_checked`. |

## Requirements Coverage (REQUIREMENTS.md; the plans' `requirements:` are empty, so these are all unclaimed by the plans)

| ID | Status | Evidence |
|----|--------|----------|
| CORE-01 | PARTIAL | Job queue, isolated worker and the scan_check schema exist. Per-check status is never written by the worker. |
| CORE-02 | PARTIAL | SSE stream and live page exist (read side). No data source for per-check rows yet. |
| CORE-03 | BLOCKED | No proof storage, no masking. |
| CORE-04 | BLOCKED | No audit log. |

All 4 IDs are accounted for. REQUIREMENTS.md still marks all as Pending. ROADMAP marks Phase 1 complete, which is not supported for CORE-03 and CORE-04.

## Anti-Patterns

No TBD/FIXME/XXX/TODO markers found. The `ScanCheck` registry is intentionally empty, and the job says explicitly that nothing was checked. This is honest, not a fabricated result. It means no real check has ever flowed through the pipeline.

## Gaps Summary

The queue, worker isolation and SSE read path are implemented in code but unexecuted. The write path for per-check status is missing, so success criterion 3 cannot occur. Proof storage and masking (CORE-03) and the audit log (CORE-04) are absent. Phase 1 should not be treated as complete. Close the gaps in a gap-closure plan, then run the human verification items.
