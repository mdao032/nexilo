package com.nexilo.common.exception;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import com.nexilo.user.quota.PlanLimitExceededException;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Gestionnaire global d'exceptions pour toute l'API Nexilo.
 *
 * <p>Toutes les réponses d'erreur suivent le format {@link ErrorResponse} :
 * <pre>
 * {
 *   "timestamp": "...",
 *   "status": 404,
 *   "errorCode": "DOCUMENT_NOT_FOUND",
 *   "message": "...",
 *   "path": "/api/v1/documents/42"
 * }
 * </pre>
 *
 * <p>Ordre de priorité des handlers (du plus spécifique au plus général) :
 * <ol>
 *   <li>NexiloException (et sous-classes)</li>
 *   <li>Validation (MethodArgumentNotValidException)</li>
 *   <li>Multipart / fichiers (MultipartException, MaxUploadSizeExceededException)</li>
 *   <li>Sécurité (BadCredentialsException, AccessDeniedException)</li>
 *   <li>HTTP (méthode non supportée, media type, JSON malformé)</li>
 *   <li>Base de données (DataIntegrityViolationException)</li>
 *   <li>Fallback 500 (Exception)</li>
 * </ol>
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // =========================================================================
    // 1b. PlanLimitExceededException — HTTP 429
    // =========================================================================

    /**
     * Gère le dépassement de quota du plan (HTTP 429 Too Many Requests).
     * Retourne un corps enrichi avec le feature bloqué et l'URL d'upgrade.
     */
    @ExceptionHandler(PlanLimitExceededException.class)
    public ResponseEntity<Map<String, Object>> handlePlanLimitExceeded(
            PlanLimitExceededException ex, HttpServletRequest request) {
        log.warn("Quota dépassé — feature={}, plan={}, limit={}, path={}",
                ex.getFeature(), ex.getPlan(), ex.getLimit(), request.getRequestURI());
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(Map.of(
                "timestamp",   Instant.now().toString(),
                "status",      429,
                "errorCode",   ErrorCode.PLAN_LIMIT_EXCEEDED.name(),
                "message",     ex.getMessage(),
                "feature",     ex.getFeature().name(),
                "limit",       ex.getLimit(),
                "plan",        ex.getPlan().name(),
                "upgradeUrl",  "/pricing",
                "path",        request.getRequestURI()
        ));
    }

    // =========================================================================
    // 1. NexiloException — exception métier de base (et ses sous-classes)
    // =========================================================================

    /**
     * Gère toutes les {@link NexiloException} (et sous-classes : ResourceNotFoundException,
     * BusinessException, etc.). Retourne le statut HTTP défini dans l'exception.
     */
    @ExceptionHandler(NexiloException.class)
    public ResponseEntity<ErrorResponse> handleNexiloException(NexiloException ex,
                                                                HttpServletRequest request) {
        log.warn("[{}] {} — path={}", ex.getErrorCode(), ex.getMessage(), request.getRequestURI());
        return ResponseEntity
                .status(ex.getHttpStatus())
                .body(ErrorResponse.of(
                        ex.getHttpStatus().value(),
                        ex.getErrorCode(),
                        ex.getMessage(),
                        request.getRequestURI()
                ));
    }

    // =========================================================================
    // 2. Validation — @Valid / @Validated
    // =========================================================================

    /**
     * Gère les erreurs de validation Bean Validation (@NotNull, @Email, etc.).
     * Liste tous les champs invalides dans le champ {@code details}.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex,
                                                          HttpServletRequest request) {
        List<String> details = ex.getBindingResult().getFieldErrors().stream()
                .map(err -> "'" + err.getField() + "' " + err.getDefaultMessage())
                .collect(Collectors.toList());

        log.warn("Validation échouée sur {} — {}", request.getRequestURI(), details);

        return ResponseEntity
                .badRequest()
                .body(ErrorResponse.withDetails(
                        HttpStatus.BAD_REQUEST.value(),
                        ErrorCode.VALIDATION_ERROR,
                        "La requête contient " + details.size() + " erreur(s) de validation",
                        request.getRequestURI(),
                        details
                ));
    }

    // =========================================================================
    // 3. Multipart / Upload de fichiers
    // =========================================================================

    /**
     * Gère le dépassement de la taille maximale d'upload (HTTP 413).
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleMaxUploadSize(MaxUploadSizeExceededException ex,
                                                              HttpServletRequest request) {
        log.warn("Fichier trop volumineux sur {} — {}", request.getRequestURI(), ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(ErrorResponse.of(
                        HttpStatus.PAYLOAD_TOO_LARGE.value(),
                        ErrorCode.FILE_TOO_LARGE,
                        "Le fichier dépasse la taille maximale autorisée",
                        request.getRequestURI()
                ));
    }

    /**
     * Gère les erreurs multipart génériques (fichier manquant, malformé, etc.).
     */
    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<ErrorResponse> handleMultipart(MultipartException ex,
                                                         HttpServletRequest request) {
        log.warn("Erreur multipart sur {} — {}", request.getRequestURI(), ex.getMessage());
        return ResponseEntity
                .badRequest()
                .body(ErrorResponse.of(
                        HttpStatus.BAD_REQUEST.value(),
                        ErrorCode.INVALID_FILE_TYPE,
                        "Erreur lors du traitement du fichier : " + ex.getMessage(),
                        request.getRequestURI()
                ));
    }

    // =========================================================================
    // 4. Sécurité
    // =========================================================================

    /**
     * Gère les identifiants incorrects (HTTP 401).
     */
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleBadCredentials(BadCredentialsException ex,
                                                               HttpServletRequest request) {
        log.warn("Authentification échouée sur {}", request.getRequestURI());
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(ErrorResponse.of(
                        HttpStatus.UNAUTHORIZED.value(),
                        ErrorCode.INVALID_CREDENTIALS,
                        "Email ou mot de passe incorrect",
                        request.getRequestURI()
                ));
    }

    /**
     * Gère les accès refusés (HTTP 403).
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex,
                                                            HttpServletRequest request) {
        log.warn("Accès refusé sur {} — {}", request.getRequestURI(), ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(ErrorResponse.of(
                        HttpStatus.FORBIDDEN.value(),
                        ErrorCode.ACCESS_DENIED,
                        "Vous n'avez pas les droits pour accéder à cette ressource",
                        request.getRequestURI()
                ));
    }

    // =========================================================================
    // 5. Erreurs HTTP courantes
    // =========================================================================

    /**
     * Méthode HTTP non supportée (HTTP 405).
     */
    @ExceptionHandler(org.springframework.web.HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotSupported(
            org.springframework.web.HttpRequestMethodNotSupportedException ex,
            HttpServletRequest request) {
        log.warn("Méthode {} non supportée sur {}", ex.getMethod(), request.getRequestURI());
        return ResponseEntity
                .status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(ErrorResponse.of(
                        HttpStatus.METHOD_NOT_ALLOWED.value(),
                        ErrorCode.METHOD_NOT_ALLOWED,
                        "Méthode HTTP non supportée : " + ex.getMethod(),
                        request.getRequestURI()
                ));
    }

    /**
     * Content-Type non supporté (HTTP 415).
     */
    @ExceptionHandler(org.springframework.web.HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMediaTypeNotSupported(
            org.springframework.web.HttpMediaTypeNotSupportedException ex,
            HttpServletRequest request) {
        log.warn("Content-Type non supporté sur {} — {}", request.getRequestURI(), ex.getContentType());
        return ResponseEntity
                .status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                .body(ErrorResponse.of(
                        HttpStatus.UNSUPPORTED_MEDIA_TYPE.value(),
                        ErrorCode.UNSUPPORTED_MEDIA_TYPE,
                        "Content-Type non supporté. Utilisez 'application/json' ou 'multipart/form-data'",
                        request.getRequestURI()
                ));
    }

    /**
     * Corps JSON malformé ou illisible (HTTP 400).
     */
    @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleMessageNotReadable(
            org.springframework.http.converter.HttpMessageNotReadableException ex,
            HttpServletRequest request) {
        log.warn("Corps de requête illisible sur {} — {}", request.getRequestURI(), ex.getMessage());
        return ResponseEntity
                .badRequest()
                .body(ErrorResponse.of(
                        HttpStatus.BAD_REQUEST.value(),
                        ErrorCode.MALFORMED_REQUEST,
                        "Corps de la requête illisible ou JSON malformé",
                        request.getRequestURI()
                ));
    }

    // =========================================================================
    // 6. Base de données
    // =========================================================================

    /**
     * Violation de contrainte de base de données (HTTP 409 — Conflict).
     */
    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrityViolation(
            org.springframework.dao.DataIntegrityViolationException ex,
            HttpServletRequest request) {
        String cause = ex.getMostSpecificCause().getMessage();
        log.error("Violation de contrainte DB sur {} — {}", request.getRequestURI(), cause);

        // Détecter les doublons (clé unique)
        boolean isDuplicate = cause != null &&
                (cause.contains("unique") || cause.contains("duplicate") || cause.contains("unicité"));

        ErrorCode code = isDuplicate ? ErrorCode.DUPLICATE_RESOURCE : ErrorCode.DATABASE_ERROR;
        String message = isDuplicate
                ? "Une ressource avec ces données existe déjà"
                : "Erreur de contrainte base de données";

        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of(HttpStatus.CONFLICT.value(), code, message,
                        request.getRequestURI()));
    }

    // =========================================================================
    // 7. Fallback — toute exception non gérée
    // =========================================================================

    /**
     * Gestionnaire de dernier recours (HTTP 500).
     * Logue l'exception complète mais retourne un message générique au client
     * (ne pas exposer les détails internes en production).
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex,
                                                          HttpServletRequest request) {
        log.error("Erreur inattendue sur {} — {}", request.getRequestURI(), ex.getMessage(), ex);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse.of(
                        HttpStatus.INTERNAL_SERVER_ERROR.value(),
                        ErrorCode.INTERNAL_ERROR,
                        "Une erreur interne est survenue. Veuillez réessayer ou contacter le support.",
                        request.getRequestURI()
                ));
    }
}
