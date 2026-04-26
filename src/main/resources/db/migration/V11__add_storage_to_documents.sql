-- =============================================================================
-- V11 — Ajout des colonnes de stockage sur summary_documents et documents
-- =============================================================================

-- Table utilisée par les services IA (résumé, Q&A, extraction)
ALTER TABLE summary_documents
    ADD COLUMN IF NOT EXISTS storage_key  VARCHAR(500),  -- chemin/clé dans le stockage
    ADD COLUMN IF NOT EXISTS storage_url  TEXT;           -- URL d'accès (pré-signée ou locale)

-- Table du module document (legacy)
ALTER TABLE documents
    ADD COLUMN IF NOT EXISTS storage_key  VARCHAR(500),
    ADD COLUMN IF NOT EXISTS storage_url  TEXT;

