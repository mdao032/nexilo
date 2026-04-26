package com.nexilo.infra.queue;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

/**
 * Entité JPA représentant un job IA asynchrone.
 *
 * <p>Un job passe par les états : PENDING → PROCESSING → DONE | FAILED.
 * En cas d'échec, il est retentable jusqu'à {@code MAX_ATTEMPTS} fois.
 */
@Entity
@Table(name = "ai_jobs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiJob {

    static final int MAX_ATTEMPTS = 3;

    @Id
    @UuidGenerator
    @Column(updatable = false, nullable = false)
    private UUID id;

    /** Type de job (SUMMARY, INGEST, QNA, EXTRACTION). */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AiJobType type;

    /** Statut courant du job. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private AiJobStatus status = AiJobStatus.PENDING;

    /** Document concerné (nullable pour les jobs sans document). */
    @Column(name = "document_id")
    private UUID documentId;

    /** Utilisateur ayant soumis le job. */
    @Column(name = "user_id")
    private Long userId;

    /** Payload JSON sérialisé (paramètres d'entrée). */
    @Column(columnDefinition = "TEXT")
    private String payload;

    /** Résultat JSON (présent uniquement si status=DONE). */
    @Column(columnDefinition = "TEXT")
    private String result;

    /** Message d'erreur (présent uniquement si status=FAILED). */
    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    /** Nombre de tentatives effectuées (max {@value MAX_ATTEMPTS}). */
    @Column(nullable = false)
    @Builder.Default
    private int attempts = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    // =========================================================================
    // Helpers de transition d'état
    // =========================================================================

    /** Marque le job comme en cours de traitement et incrémente le compteur. */
    public void markProcessing() {
        this.status = AiJobStatus.PROCESSING;
        this.startedAt = Instant.now();
        this.attempts++;
    }

    /** Marque le job comme terminé avec succès et stocke le résultat JSON. */
    public void markDone(String resultJson) {
        this.status = AiJobStatus.DONE;
        this.result = resultJson;
        this.completedAt = Instant.now();
    }

    /** Marque le job comme échoué avec un message d'erreur. */
    public void markFailed(String errorMsg) {
        this.status = AiJobStatus.FAILED;
        this.errorMessage = errorMsg;
        this.completedAt = Instant.now();
    }

    /** Remet le job en PENDING pour une nouvelle tentative (si attempts < MAX). */
    public void markRetry() {
        this.status = AiJobStatus.PENDING;
        this.startedAt = null;
    }

    /** Indique si le job peut encore être retenté. */
    public boolean canRetry() {
        return this.attempts < MAX_ATTEMPTS;
    }
}

