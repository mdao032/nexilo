package com.nexilo.infra.queue.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.nexilo.infra.queue.AiJobStatus;
import com.nexilo.infra.queue.AiJobType;

import java.time.Instant;
import java.util.UUID;

/**
 * Réponse de status d'un job IA asynchrone (polling).
 *
 * @param jobId        identifiant du job
 * @param type         type de job
 * @param status       statut courant
 * @param progress     progression en pourcentage (0-100, estimé)
 * @param result       résultat JSON (présent uniquement si status=DONE)
 * @param errorMessage message d'erreur (présent uniquement si status=FAILED)
 * @param attempts     nombre de tentatives effectuées
 * @param createdAt    date de soumission
 * @param startedAt    date de début du traitement
 * @param completedAt  date de fin
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record JobStatusResponse(
        UUID jobId,
        AiJobType type,
        AiJobStatus status,
        Integer progress,
        String result,
        String errorMessage,
        int attempts,
        Instant createdAt,
        Instant startedAt,
        Instant completedAt
) {

    /** Calcule une progression estimée en fonction du statut. */
    public static JobStatusResponse from(com.nexilo.infra.queue.AiJob job) {
        int progress = switch (job.getStatus()) {
            case PENDING     -> 0;
            case PROCESSING  -> 50;
            case DONE        -> 100;
            case FAILED      -> 0;
        };
        return new JobStatusResponse(
                job.getId(),
                job.getType(),
                job.getStatus(),
                progress,
                job.getResult(),
                job.getErrorMessage(),
                job.getAttempts(),
                job.getCreatedAt(),
                job.getStartedAt(),
                job.getCompletedAt()
        );
    }
}

