-- =============================================================================
-- V3 — Tables pour la feature de résumé PDF par IA
-- Auteur  : Nexilo
-- Date    : 2026-04-24
-- Desc    : Crée les tables summary_documents et summaries.
--           summary_documents : métadonnées du PDF uploadé + hash SHA-256
--           summaries         : résultat IA généré par Claude
-- =============================================================================

-- -----------------------------------------------------------------------------
-- TABLE : summary_documents
-- Entité : com.nexilo.ai.summary.entity.SummaryDocument
-- Clé d'idempotence : content_hash (SHA-256 du PDF)
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS summary_documents (
    id           UUID         NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    file_name    VARCHAR(255) NOT NULL,
    file_size    BIGINT       NOT NULL,
    content_hash VARCHAR(64)  NOT NULL,
    user_id      BIGINT       NOT NULL,
    uploaded_at  TIMESTAMP    NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_summary_doc_hash UNIQUE (content_hash)
);

CREATE INDEX IF NOT EXISTS idx_summary_doc_hash    ON summary_documents (content_hash);
CREATE INDEX IF NOT EXISTS idx_summary_doc_user_id ON summary_documents (user_id);

-- -----------------------------------------------------------------------------
-- TABLE : summaries
-- Entité : com.nexilo.ai.summary.entity.Summary
-- Résultat IA associé à un summary_document (1-to-1)
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS summaries (
    id          UUID         NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    document_id UUID         NOT NULL,
    content     TEXT         NOT NULL,
    language    VARCHAR(10),
    model       VARCHAR(100),
    tokens_used INTEGER,
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_summary_document
        FOREIGN KEY (document_id) REFERENCES summary_documents(id) ON DELETE CASCADE,
    CONSTRAINT uq_summary_document_id UNIQUE (document_id)
);

CREATE INDEX IF NOT EXISTS idx_summary_document_id ON summaries (document_id);

