package com.nexilo.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Exception levée pour toute violation de règle métier (HTTP 400).
 * Étend {@link NexiloException} avec {@link ErrorCode#BUSINESS_ERROR}.
 */
public class BusinessException extends NexiloException {

    public BusinessException(String message) {
        super(ErrorCode.BUSINESS_ERROR, HttpStatus.BAD_REQUEST, message);
    }
}
