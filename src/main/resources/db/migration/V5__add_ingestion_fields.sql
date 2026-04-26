-- =============================================================================
-- V5 — Champs d'ingestion sur summary_documents
-- Auteur  : Nexilo
-- Date    : 2026-04-24
-- Desc    : Ajoute les colonnes nécessaires au pipeline d'ingestion RAG :
--           - extracted_text : texte PDF extrait (évite de re-lire le PDF)
--           - ingested       : flag indiquant si le document est dans le vector store
--           - ingested_at    : horodatage de l'ingestion
-- =============================================================================

ALTER TABLE summary_documents
    ADD COLUMN IF NOT EXISTS extracted_text TEXT,
    ADD COLUMN IF NOT EXISTS ingested       BOOLEAN   NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS ingested_at    TIMESTAMP;

-- Index pour retrouver rapidement les documents non encore ingérés
CREATE INDEX IF NOT EXISTS idx_summary_doc_not_ingested
    ON summary_documents (ingested)
    WHERE ingested = FALSE;

