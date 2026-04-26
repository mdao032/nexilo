package com.nexilo.infra.queue;

/**
 * Cycle de vie d'un job IA asynchrone.
 */
public enum AiJobStatus {
    /** En attente de traitement. */
    PENDING,
    /** En cours de traitement. */
    PROCESSING,
    /** Traitement terminé avec succès. */
    DONE,
    /** Traitement échoué après le nombre maximum de tentatives. */
    FAILED
}

