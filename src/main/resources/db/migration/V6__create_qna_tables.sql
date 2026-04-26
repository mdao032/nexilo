-- =============================================================================
-- V6 — Tables pour le Q&A conversationnel sur documents PDF
-- Auteur  : Nexilo
-- Date    : 2026-04-24
-- Desc    : Crée les tables qna_sessions et qna_messages.
--           qna_sessions : session de conversation liée à un document
--           qna_messages : messages (question/réponse) de la session
-- =============================================================================

-- -----------------------------------------------------------------------------
-- TABLE : qna_sessions
-- Une session = une conversation d'un utilisateur sur un document PDF
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS qna_sessions (
    id                UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    document_id       UUID        NOT NULL,
    user_id           BIGINT      NOT NULL,
    created_at        TIMESTAMP   NOT NULL DEFAULT NOW(),
    last_activity_at  TIMESTAMP   NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_session_document
        FOREIGN KEY (document_id) REFERENCES summary_documents(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_qna_sessions_document_id ON qna_sessions (document_id);
CREATE INDEX IF NOT EXISTS idx_qna_sessions_user_id     ON qna_sessions (user_id);

-- -----------------------------------------------------------------------------
-- TABLE : qna_messages
-- Chaque message appartient à une session (rôle USER ou ASSISTANT)
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS qna_messages (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id  UUID        NOT NULL,
    role        VARCHAR(20) NOT NULL,    -- USER ou ASSISTANT
    content     TEXT        NOT NULL,
    created_at  TIMESTAMP   NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_message_session
        FOREIGN KEY (session_id) REFERENCES qna_sessions(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_qna_messages_session_id ON qna_messages (session_id);
CREATE INDEX IF NOT EXISTS idx_qna_messages_created_at ON qna_messages (session_id, created_at);

