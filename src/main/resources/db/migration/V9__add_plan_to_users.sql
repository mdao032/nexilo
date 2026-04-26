-- =============================================================================
-- V9 — Ajout du plan d'abonnement sur nexilo_users
-- =============================================================================

ALTER TABLE nexilo_users
    ADD COLUMN IF NOT EXISTS plan VARCHAR(20) NOT NULL DEFAULT 'FREE';

ALTER TABLE nexilo_users
    ADD COLUMN IF NOT EXISTS plan_expires_at TIMESTAMP;

-- Index pour requêtes par plan (statistiques, upgrade, expiration)
CREATE INDEX IF NOT EXISTS idx_users_plan ON nexilo_users (plan);

