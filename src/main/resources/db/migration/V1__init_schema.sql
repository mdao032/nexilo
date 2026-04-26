-- =============================================================================
-- V1 — Schéma initial Nexilo
-- Auteur  : Nexilo
-- Date    : 2026-04-23
-- Desc    : Création de toutes les tables du domaine.
--           Ce script est la "source de vérité" du schéma.
--           Avec baseline-on-migrate=true, il est marqué comme déjà appliqué
--           si la base existe déjà (migration Flyway initiale).
-- =============================================================================

-- -----------------------------------------------------------------------------
-- TABLE : nexilo_users
-- Entité : com.nexilo.user.entity.User
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS nexilo_users (
    id         BIGSERIAL    PRIMARY KEY,
    email      VARCHAR(255) NOT NULL UNIQUE,
    password   VARCHAR(255) NOT NULL,
    role       VARCHAR(50),                          -- enum Role : USER, ADMIN
    created_at TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_users_email ON nexilo_users (email);

-- -----------------------------------------------------------------------------
-- TABLE : documents
-- Entité : com.nexilo.document.entity.Document
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS documents (
    id           BIGSERIAL    PRIMARY KEY,
    title        VARCHAR(255) NOT NULL,
    description  TEXT,
    content_type VARCHAR(255),
    size         BIGINT,
    path         VARCHAR(255) NOT NULL,
    status       VARCHAR(50)  NOT NULL DEFAULT 'PENDING', -- enum Status : PENDING, PROCESSING, DONE, FAILED
    created_at   TIMESTAMP    NOT NULL,
    updated_at   TIMESTAMP
);

-- -----------------------------------------------------------------------------
-- TABLE : processing_jobs
-- Entité : com.nexilo.processing.entity.ProcessingJob
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS processing_jobs (
    id          BIGSERIAL   PRIMARY KEY,
    job_type    VARCHAR(50) NOT NULL,               -- enum JobType : SUMMARY, QA, EXTRACTION, GENERATION
    status      VARCHAR(50) NOT NULL,               -- enum JobStatus : PENDING, PROCESSING, COMPLETED, FAILED
    input_data  TEXT,
    result_data TEXT,
    created_at  TIMESTAMP   NOT NULL,
    updated_at  TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_processing_jobs_status   ON processing_jobs (status);
CREATE INDEX IF NOT EXISTS idx_processing_jobs_job_type ON processing_jobs (job_type);

-- -----------------------------------------------------------------------------
-- TABLE : processed_result
-- Entité : com.nexilo.document.entity.ProcessedResult
-- Hibernate génère "processed_result" (snake_case du nom de classe)
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS processed_result (
    id          BIGSERIAL PRIMARY KEY,
    document_id BIGINT REFERENCES documents(id)        ON DELETE SET NULL,
    job_id      BIGINT REFERENCES processing_jobs(id)  ON DELETE CASCADE,
    summary     TEXT,
    insights    TEXT,
    created_at  TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_processed_result_job_id ON processed_result (job_id);

-- -----------------------------------------------------------------------------
-- TABLE : ai_requests
-- Entité : com.nexilo.ai.entity.AiRequest
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ai_requests (
    id         BIGSERIAL    PRIMARY KEY,
    prompt     TEXT         NOT NULL,
    response   TEXT,
    model      VARCHAR(255),
    created_at TIMESTAMP    NOT NULL
);

-- -----------------------------------------------------------------------------
-- TABLE : subscriptions
-- Entité : com.nexilo.subscription.entity.Subscription
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS subscriptions (
    id         BIGSERIAL    PRIMARY KEY,
    user_id    BIGINT       NOT NULL,
    plan_type  VARCHAR(255) NOT NULL,
    status     VARCHAR(50)  NOT NULL,               -- enum SubscriptionStatus : ACTIVE, INACTIVE, CANCELLED, EXPIRED
    start_date TIMESTAMP    NOT NULL,
    end_date   TIMESTAMP,
    created_at TIMESTAMP    NOT NULL,
    updated_at TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_subscriptions_user_id ON subscriptions (user_id);
CREATE INDEX IF NOT EXISTS idx_subscriptions_status  ON subscriptions (status);

