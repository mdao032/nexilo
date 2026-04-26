package com.nexilo.ai.qna.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Session de conversation Q&A entre un utilisateur et un document PDF.
 * Une session regroupe un ensemble de messages (questions + réponses).
 */
@Entity
@Table(name = "qna_sessions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QnaSession {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    /** Document PDF sur lequel porte la conversation. */
    @Column(name = "document_id", nullable = false)
    private UUID documentId;

    /** Utilisateur propriétaire de la session. */
    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    /** Mis à jour à chaque nouveau message. */
    @Column(nullable = false)
    private Instant lastActivityAt;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.lastActivityAt = now;
    }
}

