package com.nexilo.infra.queue;

import com.nexilo.infra.queue.dto.JobStatusResponse;

import java.util.UUID;

/**
 * Service de gestion des jobs IA asynchrones.
 */
public interface AiJobService {

    /**
     * Soumet un job IA asynchrone.
     *
     * <p>Sauvegarde le job en base avec le statut PENDING, puis publie un
     * {@link AiJobEvent} Spring pour déclencher le traitement asynchrone.
     * Retourne immédiatement le {@code jobId} sans attendre la fin du traitement.
     *
     * @param type       type de job
     * @param documentId UUID du document concerné (peut être null)
     * @param userId     identifiant de l'utilisateur
     * @param payload    objet sérialisé en JSON comme payload
     * @return UUID du job créé
     */
    UUID submitJob(AiJobType type, UUID documentId, Long userId, Object payload);

    /**
     * Récupère le statut et le résultat d'un job.
     *
     * @param jobId  UUID du job
     * @param userId identifiant de l'utilisateur (contrôle d'accès)
     * @return réponse de statut avec progression et résultat éventuel
     */
    JobStatusResponse getJobStatus(UUID jobId, Long userId);
}

