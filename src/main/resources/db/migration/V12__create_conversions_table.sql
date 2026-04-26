-- =============================================================================
-- V12 — Table des conversions de fichiers
-- =============================================================================

CREATE TABLE conversions (
    id              UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         BIGINT        NOT NULL,
    input_format    VARCHAR(10)   NOT NULL,   -- PDF, DOCX, XLSX, PPTX, PNG, JPG…
    output_format   VARCHAR(10)   NOT NULL,
    operation       VARCHAR(30)   NOT NULL,   -- TO_WORD, TO_IMAGES, MERGE, SPLIT, COMPRESS…
    input_size_bytes  BIGINT,
    output_size_bytes BIGINT,
    status          VARCHAR(20)   NOT NULL DEFAULT 'PENDING',
    file_key        TEXT,                     -- clé MinIO/locale du fichier résultat
    job_id          UUID,                     -- référence ai_jobs.id si traitement async
    error_message   TEXT,
    created_at      TIMESTAMP     NOT NULL DEFAULT NOW(),
    expires_at      TIMESTAMP     GENERATED ALWAYS AS (created_at + INTERVAL '24 hours') STORED
);

CREATE INDEX idx_conversions_user_id    ON conversions (user_id, created_at DESC);
CREATE INDEX idx_conversions_expires_at ON conversions (expires_at) WHERE status = 'DONE';
CREATE INDEX idx_conversions_status     ON conversions (status);

