package com.nexilo.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Exception levée quand une ressource demandée est introuvable (HTTP 404).
 * Étend {@link NexiloException} avec {@link ErrorCode#RESOURCE_NOT_FOUND}.
 */
public class ResourceNotFoundException extends NexiloException {

    public ResourceNotFoundException(String message) {
        super(ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND, message);
    }
}
