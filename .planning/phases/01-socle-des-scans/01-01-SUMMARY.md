---
phase: 01-socle-des-scans
plan: 01
subsystem: scan-pipeline
tags: [spring-boot, postgres, flyway, docker-compose, job-queue]
requires: []
provides:
  - "POST /api/scans and GET /api/scans/{id} (api)"
  - "scan_job and scan_check tables (Flyway V1)"
  - "worker polling with FOR UPDATE SKIP LOCKED"
  - "docker-compose: postgres, api, worker as separate services"
affects: [01-02]
key-files:
  created:
    - api/pom.xml
    - api/src/main/java/com/sentinelscan/scan/ScanController.java
    - api/src/main/java/com/sentinelscan/scan/ScanJobRepository.java
    - api/src/main/resources/db/migration/V1__scan_jobs.sql
    - worker/pom.xml
    - worker/src/main/java/com/sentinelscan/worker/ScanJobRunner.java
    - worker/src/main/java/com/sentinelscan/worker/JdbcScanJobStore.java
    - docker-compose.yml
    - Makefile
decisions:
  - "No security checks registered in Phase 1; a job with no checks ends 'done' with an explicit note saying nothing was checked (BRIEF section 6)"
  - "Only the api runs Flyway; the worker depends on it for the schema"
  - "scan_check rows exist only for real checks; not_checked requires a reason (DB constraint)"
metrics:
  tasks: 4
  completed: 2026-10-25
---

# Phase 1 Plan 01: Scan job queue and isolated worker

Postgres-backed scan job queue (API creates `queued` jobs, a separate worker claims them with `SELECT ... FOR UPDATE SKIP LOCKED`), with no simulated results.

## Tasks and commits

| Task | Commit |
|------|--------|
| 1 ScanController (+ pom, repository, app class, tests) | edbd691 |
| 2 ScanJobRunner (+ store, worker pom, tests) | 5d96b03 |
| 3 V1__scan_jobs.sql | 6dd54c6 |
| 4 docker-compose.yml, Dockerfiles, root Makefile | 52a2c63 |

## Verification: NOT EXECUTED

**No tests, compilation, or runtime checks were run.** Maven Central is unreachable in this environment (HTTP 403 Filtered, no local `~/.m2`), so dependencies could not be downloaded and `mvn test` failed at repository resolution. `make` and Docker are also absent, and Postgres is not available. The migration SQL, the SKIP LOCKED claim query, and docker-compose.yml were not run or validated against real tooling.

Unit tests were written but have never been run (`ScanControllerTest`, `ScanJobRunnerTest`, using in-memory fakes, no Postgres needed). The code has not been compiled, so compile errors are possible. Run `make test` with network access to confirm. Concurrency behavior of SKIP LOCKED with several workers is untested.

## Deviations from Plan

The plan's tasks were terse, so the work was derived from the objective, CONTEXT.md, and BRIEF.md as instructed.

- [Rule 3 - Blocking] Added supporting files not listed in the plan: poms, application classes, `application.yml`, `ScanJob`, `ScanJobRepository`, `ScanJobStore`, `ScanCheck`, Dockerfiles, Makefile.
- The `scan_check` table (4 statuses plus reason) is created, but nothing writes to it yet because no checks exist.
- SSE progress (mentioned in CONTEXT.md) is not part of this plan's scope and is not implemented.

## Known Stubs

None that fabricate results. The `ScanCheck` registry is intentionally empty; the job note states "No security checks are implemented yet".
