-- =============================================================================
-- V2 — Ajout du support pgvector pour la recherche sémantique
-- Auteur  : Nexilo
-- Date    : 2026-04-24
-- Desc    : Installe l'extension pgvector et crée les tables vectorielles.
--           Si pgvector n'est pas installé, les tables sont créées SANS colonne
--           embedding (dégradé gracieux) — l'app démarre quand même.
--
-- Pour installer pgvector :
--   Docker     : utiliser l'image pgvector/pgvector:pg16 (recommandé)
--   Ubuntu     : sudo apt install postgresql-16-pgvector
--   macOS      : brew install pgvector
--   Windows    : https://github.com/pgvector/pgvector/releases
-- =============================================================================

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_available_extensions WHERE name = 'vector') THEN
        EXECUTE 'CREATE EXTENSION IF NOT EXISTS vector';
        EXECUTE 'CREATE TABLE IF NOT EXISTS vector_store (
            id        UUID    DEFAULT gen_random_uuid() PRIMARY KEY,
            content   TEXT,
            metadata  JSONB,
            embedding vector(1536)
        )';
        EXECUTE 'CREATE INDEX IF NOT EXISTS vector_store_embedding_idx
            ON vector_store
            USING hnsw (embedding vector_cosine_ops)
            WITH (m = 16, ef_construction = 64)';
        RAISE NOTICE 'V2 : pgvector activé, vector_store créé avec embedding.';
    ELSE
        EXECUTE 'CREATE TABLE IF NOT EXISTS vector_store (
            id       UUID DEFAULT gen_random_uuid() PRIMARY KEY,
            content  TEXT,
            metadata JSONB
        )';
        RAISE WARNING 'V2 : pgvector NON disponible. vector_store créé sans embedding. Utilisez Docker (pgvector/pgvector:pg16) pour activer.';
    END IF;
END$$;
