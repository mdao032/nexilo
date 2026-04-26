-- =============================================================================
-- V8 — Table des jobs IA asynchrones
-- =============================================================================

CREATE TYPE ai_job_type   AS ENUM ('SUMMARY', 'INGEST', 'QNA', 'EXTRACTION');
CREATE TYPE ai_job_status AS ENUM ('PENDING', 'PROCESSING', 'DONE', 'FAILED');

CREATE TABLE ai_jobs (
    id              UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    type            ai_job_type   NOT NULL,
    status          ai_job_status NOT NULL DEFAULT 'PENDING',
    document_id     UUID,
    user_id         BIGINT,
    payload         TEXT,                        -- JSON sérialisé de la requête
    result          TEXT,                        -- JSON du résultat (si DONE)
    error_message   TEXT,                        -- message d'erreur (si FAILED)
    attempts        INTEGER       NOT NULL DEFAULT 0,
    created_at      TIMESTAMP     NOT NULL DEFAULT NOW(),
    started_at      TIMESTAMP,
    completed_at    TIMESTAMP
);

-- Index pour récupérer rapidement les jobs d'un utilisateur
CREATE INDEX idx_ai_jobs_user_id    ON ai_jobs (user_id);
-- Index pour le polling et la relance des jobs en attente
CREATE INDEX idx_ai_jobs_status     ON ai_jobs (status);
-- Index pour retrouver les jobs liés à un document
CREATE INDEX idx_ai_jobs_document   ON ai_jobs (document_id);

