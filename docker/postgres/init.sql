-- =============================================================================
-- Nexilo — Script d'initialisation PostgreSQL (Docker uniquement)
-- Exécuté automatiquement au premier démarrage du container Docker.
-- Ce script est monté via docker-entrypoint-initdb.d/
--
-- IMPORTANT : Ce script n'est exécuté QUE lors de la création initiale
--             du volume Docker (premier `docker-compose up`).
--             Pour réinitialiser : docker-compose down -v && docker-compose up -d
-- =============================================================================

-- Activation de l'extension pgvector
-- Requiert l'image pgvector/pgvector:pg16 (définie dans docker-compose.yml)
CREATE EXTENSION IF NOT EXISTS vector;

-- Activation de l'extension uuid-ossp (génération d'UUID)
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Activation de pg_trgm (recherche full-text trigram — optionnel mais utile)
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- Confirmation
DO $$
BEGIN
    RAISE NOTICE 'Nexilo DB initialisée : pgvector=%, uuid-ossp=%, pg_trgm=%',
        (SELECT installed_version FROM pg_available_extensions WHERE name = 'vector'),
        (SELECT installed_version FROM pg_available_extensions WHERE name = 'uuid-ossp'),
        (SELECT installed_version FROM pg_available_extensions WHERE name = 'pg_trgm');
END$$;

