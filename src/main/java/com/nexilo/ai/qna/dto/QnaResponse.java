package com.nexilo.ai.qna.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Réponse Q&A générée par Claude à partir du contexte RAG.
 */
@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class QnaResponse {

    /** ID de session (nouvelle ou existante). */
    private final UUID sessionId;

    /** Réponse générée par Claude. */
    private final String answer;

    /**
     * Extraits des chunks utilisés comme contexte (sources).
     * Permet à l'utilisateur de vérifier d'où vient l'information.
     */
    private final List<String> sources;

    /**
     * Score de confiance (0.0 à 1.0) basé sur la similarité cosinus
     * moyenne des chunks récupérés.
     * 1.0 = contexte parfaitement pertinent, 0.0 = aucune correspondance.
     */
    private final Double confidence;

    /** Horodatage de la réponse. */
    private final Instant answeredAt;
}

