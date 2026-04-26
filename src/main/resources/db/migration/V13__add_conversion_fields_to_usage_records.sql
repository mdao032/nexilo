-- =============================================================================
-- V13 — Ajout des colonnes de conversion dans usage_records
-- =============================================================================
-- Ces colonnes permettent de tracer les conversions de fichiers (PDF↔DOCX, etc.)
-- dans le même tableau d'usage que les opérations IA.
-- tokensUsed et costMicroUsd restent à 0 pour les conversions (coût CPU, pas tokens).

ALTER TABLE usage_records
    ADD COLUMN IF NOT EXISTS input_format    VARCHAR(20),   -- "PDF", "DOCX", "XLSX"...
    ADD COLUMN IF NOT EXISTS output_format   VARCHAR(20),   -- "DOCX", "XLSX", "PNG", "ZIP"...
    ADD COLUMN IF NOT EXISTS input_size_bytes  BIGINT,      -- taille fichier source en octets
    ADD COLUMN IF NOT EXISTS output_size_bytes BIGINT;      -- taille fichier résultat en octets

-- Index pour les requêtes analytiques par format
CREATE INDEX IF NOT EXISTS idx_usage_input_format  ON usage_records (input_format)  WHERE input_format IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_usage_output_format ON usage_records (output_format) WHERE output_format IS NOT NULL;

