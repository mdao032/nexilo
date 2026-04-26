package com.nexilo.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Exception de base pour toutes les erreurs métier de l'application Nexilo.
 * Porte un {@link ErrorCode} et un {@link HttpStatus} pour une réponse HTTP précise.
 *
 * <p>Usage recommandé :
 * <pre>
 *   throw new NexiloException(ErrorCode.AI_SERVICE_ERROR, HttpStatus.SERVICE_UNAVAILABLE,
 *       "Le service IA est temporairement indisponible");
 * </pre>
 */
@Getter
public class NexiloException extends RuntimeException {

    /** Code d'erreur fonctionnel. */
    private final ErrorCode errorCode;

    /** Statut HTTP à retourner au client. */
    private final HttpStatus httpStatus;

    public NexiloException(ErrorCode errorCode, HttpStatus httpStatus, String message) {
        super(message);
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
    }

    public NexiloException(ErrorCode errorCode, HttpStatus httpStatus, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
    }

    // -------------------------------------------------------------------------
    // Fabriques statiques pour les cas les plus fréquents
    // -------------------------------------------------------------------------

    /** Ressource introuvable (404). */
    public static NexiloException notFound(String message) {
        return new NexiloException(ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND, message);
    }

    /** Document introuvable (404). */
    public static NexiloException documentNotFound(Long id) {
        return new NexiloException(ErrorCode.DOCUMENT_NOT_FOUND, HttpStatus.NOT_FOUND,
                "Document introuvable avec l'ID : " + id);
    }

    /** Erreur du service IA (503). */
    public static NexiloException aiServiceError(String message) {
        return new NexiloException(ErrorCode.AI_SERVICE_ERROR, HttpStatus.SERVICE_UNAVAILABLE, message);
    }

    /** Erreur de traitement (422). */
    public static NexiloException processingError(String message) {
        return new NexiloException(ErrorCode.PROCESSING_ERROR, HttpStatus.UNPROCESSABLE_ENTITY, message);
    }

    /** Type de fichier invalide (415). */
    public static NexiloException invalidFileType(String message) {
        return new NexiloException(ErrorCode.INVALID_FILE_TYPE, HttpStatus.UNSUPPORTED_MEDIA_TYPE, message);
    }

    /** Fichier trop volumineux (413). */
    public static NexiloException fileTooLarge(String message) {
        return new NexiloException(ErrorCode.FILE_TOO_LARGE, HttpStatus.PAYLOAD_TOO_LARGE, message);
    }

    /** Limite du plan atteinte (402). */
    public static NexiloException planLimitExceeded(String message) {
        return new NexiloException(ErrorCode.PLAN_LIMIT_EXCEEDED, HttpStatus.PAYMENT_REQUIRED, message);
    }

    /** Erreur métier générique (400). */
    public static NexiloException businessError(String message) {
        return new NexiloException(ErrorCode.BUSINESS_ERROR, HttpStatus.BAD_REQUEST, message);
    }
}

