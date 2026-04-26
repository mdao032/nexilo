-- =============================================================================
-- V10 — Table de tracking d'usage IA (analytics + facturation)
-- =============================================================================

CREATE TABLE usage_records (
    id                UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id           BIGINT        NOT NULL,
    feature           VARCHAR(30)   NOT NULL,          -- SUMMARY / QNA / EXTRACTION
    tokens_used       INTEGER       NOT NULL DEFAULT 0,
    cost_micro_usd    BIGINT        NOT NULL DEFAULT 0, -- coût en micro-dollars (1 USD = 1 000 000)
    document_id       UUID,
    response_time_ms  BIGINT,                           -- durée de l'appel IA en ms
    plan_at_time      VARCHAR(20)   NOT NULL DEFAULT 'FREE',
    created_at        TIMESTAMP     NOT NULL DEFAULT NOW()
);

-- Index pour les requêtes analytics par utilisateur + période
CREATE INDEX idx_usage_user_date    ON usage_records (user_id, created_at DESC);
-- Index pour les agrégats par feature
CREATE INDEX idx_usage_feature      ON usage_records (feature, created_at DESC);
-- Index pour la facturation (recherche par période)
CREATE INDEX idx_usage_created_at   ON usage_records (created_at DESC);

