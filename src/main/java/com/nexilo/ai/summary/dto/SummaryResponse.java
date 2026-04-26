package com.nexilo.ai.summary.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

/**
 * Réponse de l'API de résumé PDF.
 */
@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SummaryResponse {

    /** ID unique du résumé. */
    private final UUID summaryId;

    /** ID du document source. */
    private final UUID documentId;

    /** Nom du fichier original. */
    private final String fileName;

    /** Taille du fichier en octets. */
    private final Long fileSize;

    /** Contenu du résumé généré par Claude. */
    private final String content;

    /** Langue détectée du document. */
    private final String language;

    /** Modèle IA utilisé. */
    private final String model;

    /** Tokens estimés utilisés. */
    private final Integer tokensUsed;

    /** Date de création du résumé. */
    private final Instant createdAt;

    /**
     * Indique si le résumé a été récupéré depuis le cache (idempotence)
     * ou généré à la demande.
     */
    private final boolean cached;
}

