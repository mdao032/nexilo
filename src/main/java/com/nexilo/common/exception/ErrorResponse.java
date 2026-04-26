package com.nexilo.common.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.List;

/**
 * Réponse d'erreur unifiée pour toute l'API Nexilo.
 *
 * <p>Format JSON :
 * <pre>
 * {
 *   "timestamp": "2026-04-24T10:15:30Z",
 *   "status": 404,
 *   "errorCode": "DOCUMENT_NOT_FOUND",
 *   "message": "Document introuvable avec l'ID : 42",
 *   "path": "/api/v1/documents/42",
 *   "details": ["champ email : doit être une adresse valide"]   // présent seulement si non null
 * }
 * </pre>
 */
@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponse {

    /** Horodatage de l'erreur (UTC). */
    private final Instant timestamp;

    /** Code HTTP (ex: 404, 400, 500). */
    private final int status;

    /** Code d'erreur fonctionnel Nexilo. */
    private final String errorCode;

    /** Message lisible par le développeur. */
    private final String message;

    /** Chemin de la requête ayant échoué. */
    private final String path;

    /**
     * Détails additionnels, utilisés notamment pour les erreurs de validation
     * (liste des champs invalides).
     */
    private final List<String> details;

    // -------------------------------------------------------------------------
    // Fabriques statiques
    // -------------------------------------------------------------------------

    /** Construit une réponse sans détails. */
    public static ErrorResponse of(int status, ErrorCode code, String message, String path) {
        return ErrorResponse.builder()
                .timestamp(Instant.now())
                .status(status)
                .errorCode(code.name())
                .message(message)
                .path(path)
                .build();
    }

    /** Construit une réponse avec liste de détails (validation). */
    public static ErrorResponse withDetails(int status, ErrorCode code, String message,
                                            String path, List<String> details) {
        return ErrorResponse.builder()
                .timestamp(Instant.now())
                .status(status)
                .errorCode(code.name())
                .message(message)
                .path(path)
                .details(details)
                .build();
    }
}

