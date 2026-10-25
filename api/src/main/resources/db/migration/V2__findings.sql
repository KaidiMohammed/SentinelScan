-- V2__findings.sql — Phase 2 (SEC-02): findings with redacted evidence only.
--
-- RULE: this table has NO column able to hold a secret in clear. A secret is represented only by
--   * evidence_redacted : masked value produced by com.sentinelscan.checks.Redactor
--                         (always contains '****') plus non-secret context;
--   * fingerprint       : 'sha256:' + 16 hex chars, non-reversible, for re-scan comparison.
-- The CHECK constraints below are defence in depth: they reject rows whose evidence still looks
-- like a full known secret. Do not add a plaintext / raw_value column.
--
-- Phase 1 dependency: V1 (scans table) does not exist yet. scan_id is therefore stored without a
-- foreign key; add `REFERENCES scans(id) ON DELETE CASCADE` once Phase 1 defines scans (and align
-- the type if scans.id is not UUID). Note: Flyway version overlap with another phase was flagged
-- on the team board and accepted; renumber at merge time if needed.

CREATE TABLE findings (
    id                UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    scan_id           UUID        NOT NULL,
    check_id          TEXT        NOT NULL,
    type              TEXT        NOT NULL,
    severity          TEXT        NOT NULL,
    source_url        TEXT        NOT NULL,
    line              INTEGER,
    evidence_redacted TEXT        NOT NULL,
    fingerprint       TEXT        NOT NULL,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT findings_severity_chk
        CHECK (severity IN ('critical', 'high', 'medium', 'low', 'info')),
    CONSTRAINT findings_line_chk
        CHECK (line IS NULL OR line >= 1),
    CONSTRAINT findings_fingerprint_chk
        CHECK (fingerprint ~ '^sha256:[0-9a-f]{16}$'),
    CONSTRAINT findings_evidence_masked_chk
        CHECK (position('****' IN evidence_redacted) > 0),
    CONSTRAINT findings_evidence_no_plaintext_chk
        CHECK (
            length(evidence_redacted) <= 512
            AND evidence_redacted !~ 'eyJ[A-Za-z0-9_-]{10,}\.eyJ[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,}'
            AND evidence_redacted !~ 'sb_secret_[A-Za-z0-9_-]{20,}'
            AND evidence_redacted !~ '(sk|rk)_live_[0-9A-Za-z]{20,}'
            AND evidence_redacted !~ 'sk-(proj|svcacct|admin|ant)-[A-Za-z0-9_-]{20,}'
            AND evidence_redacted !~ 'sk-[A-Za-z0-9]{20}T3BlbkFJ'
            AND evidence_redacted !~ '(gh[pousr]_[A-Za-z0-9]{36,}|github_pat_[A-Za-z0-9_]{60,})'
            AND evidence_redacted !~ '(AKIA|ASIA)[0-9A-Z]{16}'
            AND evidence_redacted !~ 'PRIVATE KEY'
        ),
    -- The same secret in the same resource is reported once per scan.
    CONSTRAINT findings_unique_per_scan
        UNIQUE (scan_id, check_id, source_url, fingerprint)
);

CREATE INDEX findings_scan_id_idx ON findings (scan_id);
-- Re-scan before/after comparison looks findings up by fingerprint.
CREATE INDEX findings_fingerprint_idx ON findings (fingerprint);

COMMENT ON TABLE findings IS
    'Scan findings. Secrets are NEVER stored in clear: only redacted evidence and a SHA-256 fingerprint.';
COMMENT ON COLUMN findings.scan_id IS
    'Scan id. FK to scans(id) to be added once the Phase 1 scans table exists.';
COMMENT ON COLUMN findings.evidence_redacted IS
    'Masked evidence (Redactor output, contains ****) + non-secret context. Never plaintext.';
COMMENT ON COLUMN findings.fingerprint IS
    'sha256: + 16 hex chars of SHA-256(secret). Non-reversible; used to compare re-scans.';
