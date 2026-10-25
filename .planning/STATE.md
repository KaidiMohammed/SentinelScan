# Project State

## Project Reference

See: .planning/PROJECT.md (updated 2026-10-23)

**Core value:** Des résultats vrais, chaque constat prouvé par une vérification réelle.
**Current focus:** Phase 1 — Socle des scans

## Current Position

Phase: 1 of 4 (Socle des scans)
Status: Ready to plan — version légère : planning et squelette seulement, aucune vérification implémentée

## Plan Progress

- Phase 2, plan 02-01 (secrets in JS bundles): code executed 2026-10-25 on feature/SENT-2-02 —
  BundleSecretsCheck, Redactor, V2__findings.sql, test-targets/leaky-bundle. Verified at check level
  only (javac + /tmp harness, 21/21); `make test` does not exist yet; SQL not run on Postgres.
  See .planning/phases/02-secrets-exposes-dans-les-bundles/02-01-SUMMARY.md

## Decisions

- [02-01] Supabase JWTs: only role=service_role is reported (anon is public by design)
- [02-01] Redaction: < 16 chars fully masked; else type prefix + **** + last 4 (visible <= 1/3); fingerprint sha256:16hex
- [02-01] findings table holds no plaintext column; CHECK constraints reject unmasked evidence

## Blockers / Concerns

- Phase 2 depends on Phase 1 (not implemented): Check interface, job queue, scans table (V1), build/`make test`
  are missing; 02-01 must be wired once Phase 1 lands. SEC-01..03 not marked complete until end-to-end proof.
- Flyway V2 version overlap with another phase (flagged on board, accepted) — renumber at merge if needed.

## Session

Last session: 2026-10-25 — Completed 02-01-PLAN.md (check level). Resume: Phase 1 implementation.
