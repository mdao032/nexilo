package com.nexilo.common.exception;

import java.time.LocalDateTime;

/**
 * @deprecated Remplacé par {@link ErrorResponse}.
 * Conservé temporairement pour compatibilité, sera supprimé dans V2.
 */
@Deprecated(since = "0.0.2", forRemoval = true)
public class ApiError {
    public LocalDateTime timestamp;
    public int status;
    public String error;
    public String message;

    public ApiError(LocalDateTime timestamp, int status, String error, String message) {
        this.timestamp = timestamp;
        this.status = status;
        this.error = error;
        this.message = message;
    }
}
