-- =============================================================================
-- V4 — Table document_chunks pour le RAG (Retrieval-Augmented Generation)
-- Auteur  : Nexilo
-- Date    : 2026-04-24
-- Desc    : Crée la table des chunks vectorisés.
--           Si pgvector n'est pas disponible, la table est créée SANS colonne
--           embedding (dégradé gracieux) — l'app démarre quand même.
-- =============================================================================

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_available_extensions WHERE name = 'vector') THEN
        EXECUTE 'CREATE EXTENSION IF NOT EXISTS vector';
        EXECUTE 'CREATE TABLE IF NOT EXISTS document_chunks (
            id           UUID      PRIMARY KEY DEFAULT gen_random_uuid(),
            document_id  UUID      NOT NULL,
            content      TEXT      NOT NULL,
            embedding    vector(1536),
            chunk_index  INTEGER   NOT NULL,
            metadata     JSONB,
            created_at   TIMESTAMP NOT NULL DEFAULT NOW(),
            CONSTRAINT fk_chunk_document
                FOREIGN KEY (document_id)
                REFERENCES summary_documents(id) ON DELETE CASCADE,
            CONSTRAINT uq_chunk_position UNIQUE (document_id, chunk_index)
        )';
        EXECUTE 'CREATE INDEX IF NOT EXISTS idx_chunks_embedding_cosine
            ON document_chunks
            USING ivfflat (embedding vector_cosine_ops)
            WITH (lists = 100)';
        RAISE NOTICE 'V4 : document_chunks créé avec embedding (pgvector actif).';
    ELSE
        EXECUTE 'CREATE TABLE IF NOT EXISTS document_chunks (
            id           UUID      PRIMARY KEY DEFAULT gen_random_uuid(),
            document_id  UUID      NOT NULL,
            content      TEXT      NOT NULL,
            chunk_index  INTEGER   NOT NULL,
            metadata     JSONB,
            created_at   TIMESTAMP NOT NULL DEFAULT NOW(),
            CONSTRAINT fk_chunk_document
                FOREIGN KEY (document_id)
                REFERENCES summary_documents(id) ON DELETE CASCADE,
            CONSTRAINT uq_chunk_position UNIQUE (document_id, chunk_index)
        )';
        RAISE WARNING 'V4 : document_chunks créé SANS embedding (pgvector absent). Utilisez Docker pour activer pgvector.';
    END IF;

    EXECUTE 'CREATE INDEX IF NOT EXISTS idx_chunks_document_id ON document_chunks (document_id)';
    EXECUTE 'CREATE INDEX IF NOT EXISTS idx_chunks_position ON document_chunks (document_id, chunk_index)';
END$$;
