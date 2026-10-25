# Phase 1: Socle des scans - Context

Job en file Postgres (table scan_job, verrouillage SKIP LOCKED), worker Spring Boot dans son conteneur,
progression en direct par SSE, statut par vérification : running, finding, clean, not_checked (+ raison).
