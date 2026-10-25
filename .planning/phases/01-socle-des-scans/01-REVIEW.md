---
phase: 01-socle-des-scans
reviewed: 2026-10-25T00:00:00Z
depth: deep
files_reviewed: 26
files_reviewed_list:
  - Makefile
  - api/Dockerfile
  - api/pom.xml
  - api/src/main/java/com/sentinelscan/ApiApplication.java
  - api/src/main/java/com/sentinelscan/scan/ScanCheckRow.java
  - api/src/main/java/com/sentinelscan/scan/ScanController.java
  - api/src/main/java/com/sentinelscan/scan/ScanJob.java
  - api/src/main/java/com/sentinelscan/scan/ScanJobRepository.java
  - api/src/main/java/com/sentinelscan/scan/ScanProgressStream.java
  - api/src/main/resources/application.yml
  - api/src/main/resources/db/migration/V1__scan_jobs.sql
  - api/src/test/java/com/sentinelscan/scan/ScanControllerTest.java
  - api/src/test/java/com/sentinelscan/scan/ScanProgressStreamTest.java
  - docker-compose.yml
  - web/app/page.tsx
  - web/app/scan/[id]/page.tsx
  - web/next.config.mjs
  - worker/Dockerfile
  - worker/pom.xml
  - worker/src/main/java/com/sentinelscan/worker/JdbcScanJobStore.java
  - worker/src/main/java/com/sentinelscan/worker/ScanCheck.java
  - worker/src/main/java/com/sentinelscan/worker/ScanJobRunner.java
  - worker/src/main/java/com/sentinelscan/worker/ScanJobStore.java
  - worker/src/main/java/com/sentinelscan/worker/WorkerApplication.java
  - worker/src/main/resources/application.yml
  - worker/src/test/java/com/sentinelscan/worker/ScanJobRunnerTest.java
findings:
  critical: 4
  warning: 14
  info: 9
  total: 27
status: issues_found
---

# Phase 01: Code Review Report

**Reviewed:** 2026-10-25
**Depth:** deep
**Files Reviewed:** 26
**Status:** issues_found

## Summary

I reviewed every source file changed in `97c59dd..HEAD` (excluding `.planning/`). Nothing could be compiled, so I type-checked the Java and TSX by hand and checked the Spring 6.1 / Boot 3.3, JdbcTemplate, SseEmitter, Next 16 and Flyway 10 APIs they use.

**Compile/SQL:** I found no definite compile errors. The `JdbcTemplate.query(sql, (rs, i) -> ..., args)` lambdas resolve unambiguously by arity. `ResponseEntity.notFound().build()` infers its type correctly in both controllers. `ResponseEntity<SseEmitter>` is a supported return type. `@PathVariable UUID id` relies on `-parameters`, which `spring-boot-starter-parent` provides. `flyway-database-postgresql` is present, as Flyway 10 requires. The migration is valid PostgreSQL. The JSON field names (`targetUrl`, `checkKey`, ...) match the TS types. One commit on the history does not compile on its own (IN-04).

**Truthfulness (BRIEF §6):** Phase 1 does not fabricate any finding today, and the "no checks ran" path is honest. But the pipeline this phase delivers cannot report the truth once a check exists:
- A failing check fails the whole job instead of showing "n'a pas pu être vérifié" (CR-01).
- Nothing records evidence for a `finding` (CR-02).
- A crashed worker leaves a job shown as "Scan en cours" forever (CR-03).

The SSE endpoint polls the database once per connection, has no disconnect detection, and runs on a fixed 2-thread pool. That is a leak and a DoS vector (CR-04). Phase requirements CORE-03 and CORE-04 are mapped to this phase but not implemented.

## Critical Issues

### CR-01: A failing check fails the whole job instead of recording "not_checked + reason"; nothing ever writes per-check status

**File:** `worker/src/main/java/com/sentinelscan/worker/ScanJobRunner.java:42-50`, `worker/src/main/java/com/sentinelscan/worker/ScanCheck.java:7-11`
**Issue:** Phase success criterion 3 and BRIEF §6 say: "a check that fails appears as 'could not be checked' with the reason." The runner does the opposite:
- The first exception from any check aborts the loop and marks the **entire job** `failed`.
- The remaining checks never run, and nothing records them.
- The failing check gets no `scan_check` row, so the UI cannot show it as `not_checked`.

`ScanCheck.run` returns `void`, so the runner cannot know whether a check produced `finding` or `clean`. No code path in the worker inserts or updates `scan_check` at all, even though `ScanCheckRow`, the SSE stream and the UI all depend on those rows. CORE-01 and CORE-02 ("each check really in progress") are therefore unimplemented.

The runner also never reconciles: if a check wrote `running` and then threw, that row stays `running` forever. The UI then shows "En cours…" next to "Le scan a échoué" (`web/app/scan/[id]/page.tsx:13,66`). That is a false live status.

**Fix:** Make the check return a result, and let the runner own the `scan_check` lifecycle:
```java
public sealed interface CheckOutcome permits Finding, Clean, NotChecked { }
public record NotChecked(String reason) implements CheckOutcome { }
// ScanCheck: CheckOutcome run(UUID jobId, String targetUrl) throws Exception;

for (ScanCheck check : checks) {
    store.upsertCheck(job.id(), check.key(), "running", null);
    try {
        CheckOutcome o = check.run(job.id(), job.targetUrl());
        store.recordOutcome(job.id(), check.key(), o);   // finding/clean/not_checked
    } catch (Exception e) {
        store.upsertCheck(job.id(), check.key(), "not_checked", safeReason(e));
    }
}
store.markDone(job.id(), checks.isEmpty() ? NO_CHECKS_NOTE : null);
```
Reserve job-level `failed` for infrastructure failures. On any terminal transition, convert leftover `running` rows to `not_checked` with a reason.

### CR-02: The schema and UI allow a "finding" with no evidence (CORE-03 / BRIEF §6 not implemented)

**File:** `api/src/main/resources/db/migration/V1__scan_jobs.sql:18-28`, `web/app/scan/[id]/page.tsx:12-16,64-68`
**Issue:** The phase goal says each check reports "un statut vrai **et une preuve**". CORE-03, assigned to this phase, says every finding stores its raw evidence. `scan_check` has `status IN ('running','finding','clean','not_checked')` and only a `reason` column:
- There is no evidence column or table.
- No constraint ties `finding` to stored evidence.
- One check cannot hold several findings.

The UI renders "Problème trouvé" from the bare status, with nothing proving it. A row with `status='finding'` and no proof is accepted by the database and displayed to the user. The project rule forbids exactly this.
**Fix:** Add a `scan_finding` table (job id, check key, severity from the check, title, `evidence_request`, `evidence_response_excerpt` / `tool_output`, `NOT NULL` evidence, secrets masked). Then do two things:
- Enforce the link: either a deferred constraint or trigger, or derive `finding` status only from the existence of `scan_finding` rows.
- Have the UI show the evidence next to every finding and never render "Problème trouvé" without it.

If this is deferred, remove `'finding'` from the CHECK list until evidence storage exists.

### CR-03: Jobs orphaned in `running` are never recovered, so the UI shows "Scan en cours" forever

**File:** `worker/src/main/java/com/sentinelscan/worker/JdbcScanJobStore.java:13-15,23-33`, `worker/src/main/java/com/sentinelscan/worker/ScanJobRunner.java:46-50`, `docker-compose.yml:37`
**Issue:** `claimNext` commits `status='running'` and releases the row lock immediately. From then on, the job's liveness is only in worker memory. If the worker is killed, OOMs or is redeployed, the job stays `running` permanently. `restart: unless-stopped` makes this a routine event. The restarted worker only claims `queued` rows, and there is no lease, heartbeat or reaper.

It also happens without a crash: if `markDone` throws (DB blip), the `catch` calls `markFailed`. If that also throws, the exception escapes `poll()` and the row stays `running`.

The SSE stream and the page then report "Scan en cours" indefinitely. That is the fake spinner BRIEF §8 forbids ("Live scan progress that reflects real check execution — no fake spinners").
**Fix:** Add `heartbeat_at` / `locked_until TIMESTAMPTZ` to `scan_job`. The worker refreshes it while running. A scheduled reaper fails jobs that pass their lease, with a truthful error:
```sql
UPDATE scan_job SET status='failed', error='Le worker s''est arrêté pendant le scan', finished_at=now()
WHERE status='running' AND locked_until < now();
```
Alternatively, re-queue them with an attempt counter. Mark remaining `running` checks `not_checked` with that reason (see CR-01).

### CR-04: SSE streams leak polling tasks and share a fixed 2-thread pool, so one slow or abandoned client stalls everyone's live progress (unauthenticated DoS)

**File:** `api/src/main/java/com/sentinelscan/scan/ScanProgressStream.java:28-29,39,50-63,75-78`
**Issue:**
- **Disconnects are only detected on a failed write**, and a write only happens when the state changed (`changed(...)`). There is no heartbeat. A closed tab, or a Next proxy that dropped the upstream (see WR-06), watching a `queued` or stuck `running` job keeps a 1 Hz task running 2 SQL queries for up to `MAX_STREAM_MS` = 15 min. Then EventSource auto-reconnect starts a new one.
- **All streams share `Executors.newScheduledThreadPool(2)`.** `emitter.send` performs a blocking servlet write, so a client that stops reading fills the TCP buffer and pins one of the two threads. Two such clients freeze every live-progress stream in the API. Even without blocking, N open streams cause 2N queries per second that funnel through 2 threads, so progress latency grows with the number of viewers.
- **The endpoint is unauthenticated and has no per-client or global cap.** Anyone can open thousands of streams against one valid job id.

**Fix:**
- Send a periodic comment heartbeat (`SseEmitter.event().comment("hb")`) every ~15 s so dead clients are detected and removed.
- Cap concurrent emitters (global and per IP) and reject the excess with 429.
- Do not poll the DB per connection. Run one poller per job id fanned out to subscribers, or use Postgres `LISTEN/NOTIFY`.
- Do not do blocking writes on the shared scheduler. Hand sends off to a bounded executor or virtual threads (`spring.threads.virtual.enabled=true`), and add a send timeout.

## Warnings

### WR-01: SSE task registration race: the first tick can run before `task.set`, so `cancel` is a no-op

**File:** `api/src/main/java/com/sentinelscan/scan/ScanProgressStream.java:52-62,79-82`
**Issue:** `scheduleWithFixedDelay(..., 0, ...)` can run the first `tick` on a poller thread before `task.set(...)` returns. For an already-finished job (for example, reloading a results page), tick 1 calls `emitter.complete()` and then `cancel.run()` while `task.get()` is still `null`. The task keeps running. It only stops on a later tick, after another round of DB queries and a second `complete()` on an already-completed emitter. Any exception there goes into the `RuntimeException` branch, which tries to `send` on a completed emitter.
**Fix:** Have the tick check a `done` flag and self-cancel, or register the future before the task can run:
```java
AtomicBoolean closed = new AtomicBoolean();
Runnable cancel = () -> { closed.set(true); var f = task.get(); if (f != null) f.cancel(false); };
task.set(poller.scheduleWithFixedDelay(() -> { if (closed.get()) { var f = task.get(); if (f != null) f.cancel(false); return; } tick(...); }, POLL_MS, POLL_MS, MILLISECONDS));
```
Alternatively, send the initial snapshot synchronously and start the schedule with an initial delay.

### WR-02: The SSE executor is never shut down

**File:** `api/src/main/java/com/sentinelscan/scan/ScanProgressStream.java:39`
**Issue:** The `ScheduledExecutorService` is created in a field initializer with non-daemon threads and has no `@PreDestroy`. Closing the context (devtools restart, tests, graceful shutdown) leaves the threads alive and the scheduled tasks hitting a closed DataSource. Every `new ScanProgressStream(...)` in `ScanProgressStreamTest` also leaks 2 threads.
**Fix:** Inject a Spring-managed `ThreadPoolTaskScheduler` bean, or add `@PreDestroy void shutdown() { poller.shutdownNow(); }` and complete open emitters.

### WR-03: The claim relies entirely on the `@Transactional` proxy; the UPDATE has no status guard

**File:** `worker/src/main/java/com/sentinelscan/worker/JdbcScanJobStore.java:13-15,24-32`
**Issue:** `SELECT ... FOR UPDATE SKIP LOCKED` followed by a separate `UPDATE ... WHERE id = ?` is only correct if both run in one transaction. If the method is ever invoked without the proxy, the row lock is released right after the SELECT under autocommit, and two workers can claim the same job. Ways that happens:
- self-invocation,
- direct construction as in tests,
- a future refactor that drops `@Transactional`.

The `UPDATE` does not re-check `status = 'queued'`, and its update count is ignored, so the double claim would go unnoticed. The only test (`claimSqlUsesSkipLocked`) checks that the string contains the keyword, so it proves nothing about locking.
**Fix:** Make the claim atomic in one statement:
```sql
UPDATE scan_job SET status = 'running', started_at = now()
WHERE id = (SELECT id FROM scan_job WHERE status = 'queued'
            ORDER BY created_at LIMIT 1 FOR UPDATE SKIP LOCKED)
RETURNING id, target_url
```
Also guard `markDone`/`markFailed` with `AND status = 'running'`. Add a Testcontainers test that runs two concurrent claimers.

### WR-04: The stored error can be the literal string "null", and raw exception messages are exposed to anonymous users

**File:** `worker/src/main/java/com/sentinelscan/worker/ScanJobRunner.java:49`, `api/src/main/java/com/sentinelscan/scan/ScanJob.java:6`, `web/app/scan/[id]/page.tsx:60`
**Issue:**
- `String.valueOf(e.getMessage())` stores `"null"` for exceptions with no message (NPE and others). The UI's `?? "Raison non communiquée par le worker."` fallback never fires, and the user sees "null".
- Raw `e.getMessage()` (JDBC URLs, internal hostnames, target response fragments, possibly secrets) is persisted and served unauthenticated through `GET /api/scans/{id}` and SSE. BRIEF §7 requires secrets to be redacted.

**Fix:** Map exceptions to a user-facing reason (`timeout`, `DNS failure`, `TLS error`, ...). Store `null` when no reason is known, keep the full exception only in logs, and run any stored text through the redactor.

### WR-05: The worker starts before Flyway has migrated, and the api has no restart policy

**File:** `docker-compose.yml:16-37`
**Issue:** `condition: service_started` only waits for the api container process, not for Flyway. On first boot the worker polls a non-existent `scan_job` table and logs `relation "scan_job" does not exist` every 2 s until the migration finishes. If the api fails to start, it is never restarted (no `restart:`), so the stack stays broken.
**Fix:** Add an api healthcheck (actuator `/actuator/health`) and use `condition: service_healthy` for the worker. Alternatively, run Flyway as a one-shot `migrate` service that both depend on with `service_completed_successfully`. Add `restart: unless-stopped` to the api.

### WR-06: The Next rewrite proxy likely cuts SSE after its proxy timeout, and the UI error banner never clears

**File:** `web/next.config.mjs:5-9`, `web/app/scan/[id]/page.tsx:47-50`
**Issue:**
- External `rewrites()` go through Next's built-in HTTP proxy, which has a default upstream timeout (`experimental.proxyTimeout`, 30 s in recent versions). This is unverified on Next 16 (nothing was run). A scan that stays `queued`/`running` longer, with no change emitted, gets its stream cut.
- EventSource then auto-reconnects, but `onerror` has already set "Connexion au suivi en direct interrompue." That message is never reset when `progress` events resume, so the user sees a permanent error over a working stream.
- Each cut also leaves a server-side polling task behind (CR-04).

**Fix:** Route SSE directly to the API, or set an explicit long `experimental.proxyTimeout`, and add heartbeats. In the client:
- Clear `streamError` on `source.onopen` and on every `progress` event.
- Only show the error when `source.readyState === EventSource.CLOSED`.

### WR-07: The scan page heading always says "Analyse en cours", even when the scan is finished or failed

**File:** `web/app/scan/[id]/page.tsx:56`
**Issue:** The `<h1>` is static. For a `done` or `failed` job (including "nothing was checked"), the page title still claims an analysis is in progress. That contradicts the real status and the "no fake spinner / truthful status" rule.
**Fix:** Derive the heading from `snap?.job.status` (for example `JOB_LABEL`), with a neutral title before the first snapshot.

### WR-08: Hardcoded default DB credentials, Postgres superuser shared by api and worker, api exposed on all interfaces

**File:** `api/src/main/resources/application.yml:3-5`, `worker/src/main/resources/application.yml:3-5`, `docker-compose.yml:7,18-23`
**Issue:**
- Every layer falls back to the password `sentinelscan`, so a missing env var silently runs with a known credential.
- `POSTGRES_USER` is the cluster superuser, and both the api and the worker use it. The worker is the component that will process attacker-controlled target responses, yet it can DROP anything.
- `8080:8080` publishes the unauthenticated API on every host interface.

**Fix:**
- Remove the defaults (`${DATABASE_PASSWORD}` with no fallback) and use `${POSTGRES_PASSWORD:?set POSTGRES_PASSWORD}` in compose.
- Create least-privilege roles: the api can INSERT/SELECT `scan_job`, `scan_check`; the worker can SELECT/UPDATE `scan_job` and INSERT/UPDATE `scan_check`.
- Bind to `127.0.0.1:8080:8080` unless public exposure is intended.

### WR-09: No authorization, terms acceptance, audit log or target restrictions on scan creation (CORE-04, BRIEF §7)

**File:** `api/src/main/java/com/sentinelscan/scan/ScanController.java:28-37,44-54`
**Issue:**
- CORE-04 ("who scanned what and when") is mapped to phase 1, but there is no user identity, no audit record and no terms acceptance.
- `POST /api/scans` is anonymous and unthrottled, so anyone can enqueue unlimited jobs.
- `isHttpUrl` accepts any host, including `localhost`, `127.0.0.1`, `169.254.169.254`, `10.x`/`192.168.x`, and the compose service names `postgres` / `api`.

No check makes requests yet, so this is not exploitable today. It becomes SSRF against the internal network as soon as the first check ships.
**Fix:**
- Record `requested_by`, `requested_at`, client IP and terms version on `scan_job` (or a separate `scan_audit` table).
- Rate-limit creation.
- Resolve the host and reject loopback, link-local, private, multicast and unresolvable addresses. Re-check at connect time in the worker to defeat DNS rebinding.
- Block non-default ports unless they are allowed.

### WR-10: "Worker isolé" is not true: the worker sits on the same network as api and Postgres, with unrestricted egress

**File:** `docker-compose.yml:28-37`
**Issue:** The comment says "aucun port exposé, accès à Postgres uniquement", but the worker is attached to the default compose network. It can reach `api:8080` and `postgres:5432` and every other service, and it runs as root (WR-13) with superuser DB credentials (WR-08). Isolation is a stated phase deliverable.
**Fix:** Define separate networks. For example, `db` (internal) is shared by api, worker and postgres, and `egress` is used by the worker only. Do not give the worker a route to the api. Run it as a non-root user with `read_only: true`, `cap_drop: [ALL]` and `security_opt: [no-new-privileges:true]`. Fix the comment to match reality.

### WR-11: URL is validated after `trim()` but stored untrimmed; scheme check is case-sensitive; embedded credentials accepted

**File:** `api/src/main/java/com/sentinelscan/scan/ScanController.java:30-34,49-50`
**Issue:**
- `isHttpUrl` validates `url.trim()`, but `repository.enqueue(url)` stores the raw value with its surrounding whitespace, which the worker would then use.
- `HTTPS://example.com` is rejected because `getScheme()` keeps its case.
- `https://user:pass@host` is accepted. The credentials are stored in clear and shown on the scan page, which conflicts with BRIEF §7.
- There is no length limit.

**Fix:** Normalize once (`URI uri = URI.create(url.strip()).normalize()`), compare the scheme with `equalsIgnoreCase`, reject a non-null `getUserInfo()`, cap the length (for example 2048), and store `uri.toString()`.

### WR-12: Runner error handling: a successful run can be recorded as `failed`, and interrupts are swallowed

**File:** `worker/src/main/java/com/sentinelscan/worker/ScanJobRunner.java:42-50`
**Issue:**
- `store.markDone(...)` is inside the `try`. A DB error while recording success is caught as if a check had failed, and the job is marked `failed` with the DB error text.
- `catch (Exception e)` also catches `InterruptedException` (allowed by `ScanCheck.run ... throws Exception`) without restoring the interrupt flag, so shutdown cannot stop the `while (runOnce())` loop cleanly.

**Fix:** Move `markDone` out of the check-execution `try`. Handle `InterruptedException` separately: call `Thread.currentThread().interrupt()`, mark the job truthfully (for example `failed` with "worker arrêté"), and break out of `poll()`.

### WR-13: Containers run as root

**File:** `api/Dockerfile:7-11`, `worker/Dockerfile:7-10`
**Issue:** The runtime images have no `USER`, so both JVMs run as root. That matters most for the worker, which will handle untrusted content.
**Fix:** `RUN useradd -r -u 10001 app` and `USER 10001` in the runtime stage, plus the compose hardening in WR-10.

### WR-14: The tests do not exercise the risky parts (SQL, locking, migration, SSE emission)

**File:** `worker/src/test/java/com/sentinelscan/worker/ScanJobRunnerTest.java:50-53`, `api/src/test/java/com/sentinelscan/scan/ScanControllerTest.java:27`, `api/src/test/java/com/sentinelscan/scan/ScanProgressStreamTest.java`
**Issue:**
- `claimSqlUsesSkipLocked` only asserts that a string contains a keyword.
- No test runs the Flyway migration, any SQL statement or two concurrent claimers.
- No test covers `tick()` (send, complete, cancel, error branch).
- `r.getBody().toString().contains("queued")` depends on `Map.toString()` formatting.

Since nothing has been compiled or run, every DB and SSE claim in the summaries is unverified, and these tests would not catch the issues above.
**Fix:**
- Add Testcontainers (Postgres 16) tests for migration, enqueue/find, claim atomicity with two threads, and the CHECK constraints.
- Add an `SseEmitter` test that drives `tick()` through running to done, client IOException, and DB error.
- Assert on `((Map<?,?>) r.getBody()).get("status")`.

## Info

### IN-01: The worker pulls in `spring-boot-starter-web` but disables the web server

**File:** `worker/pom.xml:16`, `worker/src/main/resources/application.yml:6-7`
**Issue:** It ships Tomcat and Spring MVC in a worker that sets `web-application-type: none`, which only adds attack surface and image size.
**Fix:** Use `spring-boot-starter` (or `spring-boot-starter-json` if Jackson is needed).

### IN-02: Check list order changes as checks update

**File:** `api/src/main/java/com/sentinelscan/scan/ScanJobRepository.java:17-18`
**Issue:** `ORDER BY updated_at, check_key` moves a check to the bottom every time its status changes, so list items jump around in the live UI.
**Fix:** Order by a stable key (a `position` column or `check_key`).

### IN-03: Constraints are too permissive

**File:** `api/src/main/resources/db/migration/V1__scan_jobs.sql:6,24,27`
**Issue:**
- `reason = ''` satisfies `scan_check_reason_required`.
- A `failed` job may have a null `error`.
- `done`/`failed` do not require `finished_at`, and `running` does not require `started_at`.
- `scan_check.id` has no default, so every writer must generate UUIDs.

**Fix:** Use `CHECK (status <> 'not_checked' OR btrim(coalesce(reason,'')) <> '')`, add similar state/timestamp checks, and `DEFAULT gen_random_uuid()`.

### IN-04: Commit 1341936 does not compile on its own

**File:** `api/src/main/java/com/sentinelscan/scan/ScanProgressStream.java:98` (history)
**Issue:** It calls `repository.findChecks`, which was only added in 038d54e. This breaks `git bisect`.
**Fix:** Squash 038d54e into 1341936 before merging.

### IN-05: An unguarded `JSON.parse` in the SSE handler

**File:** `web/app/scan/[id]/page.tsx:35`
**Issue:** A malformed payload throws inside the listener. The UI silently stops updating, with no error shown.
**Fix:** Wrap the parse in try/catch and set `streamError` on failure.

### IN-06: Unknown or invalid scan id shows a misleading message

**File:** `web/app/scan/[id]/page.tsx:47-50`, `api/src/main/java/com/sentinelscan/scan/ScanProgressStream.java:47-49`
**Issue:** A 404 (or a 400 for a non-UUID id) on the SSE endpoint makes EventSource fail, and the page says "Connexion au suivi en direct interrompue" instead of "scan introuvable".
**Fix:** Fetch `GET /api/scans/{id}` first and show a not-found state.

### IN-07: Hardcoded "no checks implemented" claim on the home page

**File:** `web/app/page.tsx:45`
**Issue:** This static sentence becomes false as soon as phase 2 registers a check, so it is a future truthfulness hazard.
**Fix:** Drive it from the API (for example, a list of registered checks) or remove it, and rely on the per-scan note.

### IN-08: The `API_URL` rewrite target is fixed at build time, and the web app is missing from compose

**File:** `web/next.config.mjs:3`, `docker-compose.yml`
**Issue:** Next serializes `rewrites()` at `next build`, so changing `API_URL` at runtime has no effect. The web app has no Dockerfile or compose service, so phase success criterion 1 ("scan lancé depuis le front") cannot be exercised with `docker compose up`.
**Fix:** Document `API_URL` as a build arg and add a `web` service.

### IN-09: Inconsistent fully qualified type names instead of imports

**File:** `api/src/main/java/com/sentinelscan/scan/ScanJobRepository.java:41`, `worker/src/main/java/com/sentinelscan/worker/ScanCheck.java:10`
**Issue:** Fully qualified `java.util.List` / `java.util.UUID` are used instead of imports, unlike the other files.
**Fix:** Use regular imports.

---

_Reviewed: 2026-10-25_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: deep_
