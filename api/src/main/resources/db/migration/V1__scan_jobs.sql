-- File de jobs de scan (Postgres). Le worker réclame les jobs avec FOR UPDATE SKIP LOCKED.
CREATE TABLE scan_job (
    id          UUID PRIMARY KEY,
    target_url  TEXT        NOT NULL,
    status      TEXT        NOT NULL DEFAULT 'queued'
                CHECK (status IN ('queued', 'running', 'done', 'failed')),
    error       TEXT,
    note        TEXT,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    started_at  TIMESTAMPTZ,
    finished_at TIMESTAMPTZ
);

CREATE INDEX scan_job_queue_idx ON scan_job (created_at) WHERE status = 'queued';

-- Statut par vérification. Une ligne n'existe que si une vérification réelle a été enregistrée/exécutée.
-- 'not_checked' exige une raison ; aucun résultat n'est jamais inventé.
CREATE TABLE scan_check (
    id          UUID PRIMARY KEY,
    scan_job_id UUID        NOT NULL REFERENCES scan_job (id) ON DELETE CASCADE,
    check_key   TEXT        NOT NULL,
    status      TEXT        NOT NULL
                CHECK (status IN ('running', 'finding', 'clean', 'not_checked')),
    reason      TEXT,
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (scan_job_id, check_key),
    CONSTRAINT scan_check_reason_required CHECK (status <> 'not_checked' OR reason IS NOT NULL)
);
