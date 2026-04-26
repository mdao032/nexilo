package com.nexilo.ai.qna.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.UUID;

/**
 * Requête Q&A — question posée sur un document PDF.
 */
@Data
public class QnaRequest {

    /** Question posée par l'utilisateur (max 500 caractères). */
    @NotBlank(message = "La question est obligatoire")
    @Size(max = 500, message = "La question ne peut pas dépasser 500 caractères")
    private String question;

    /**
     * Identifiant de session optionnel.
     * Si null → une nouvelle session est créée côté serveur.
     * Si fourni → la conversation est ajoutée à la session existante.
     */
    private UUID sessionId;
}

