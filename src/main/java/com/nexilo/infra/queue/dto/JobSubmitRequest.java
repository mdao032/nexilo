package com.nexilo.infra.queue.dto;

import com.nexilo.infra.queue.AiJobType;

import java.util.UUID;

/**
 * Corps de la requête pour soumettre un job IA asynchrone.
 *
 * @param type       type de job (SUMMARY, INGEST, QNA, EXTRACTION)
 * @param documentId UUID du document concerné (obligatoire sauf pour SUMMARY depuis upload)
 * @param payload    JSON libre contenant les paramètres spécifiques au type de job
 */
public record JobSubmitRequest(
        AiJobType type,
        UUID documentId,
        String payload
) {}

