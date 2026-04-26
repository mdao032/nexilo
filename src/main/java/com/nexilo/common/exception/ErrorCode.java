package com.nexilo.common.exception;

/**
 * Codes d'erreur fonctionnels de l'application Nexilo.
 * Chaque code identifie de façon unique une catégorie d'erreur
 * et est inclus dans toutes les réponses d'erreur de l'API.
 */
public enum ErrorCode {

    // --- Ressources ---
    RESOURCE_NOT_FOUND,

    // --- Documents ---
    DOCUMENT_NOT_FOUND,
    INVALID_FILE_TYPE,
    FILE_TOO_LARGE,

    // --- Traitement IA ---
    AI_SERVICE_ERROR,
    PROCESSING_ERROR,

    // --- Abonnements ---
    PLAN_LIMIT_EXCEEDED,

    // --- Authentification / Autorisation ---
    INVALID_CREDENTIALS,
    ACCESS_DENIED,
    TOKEN_EXPIRED,

    // --- Validation / Requête ---
    VALIDATION_ERROR,
    MALFORMED_REQUEST,
    UNSUPPORTED_MEDIA_TYPE,
    METHOD_NOT_ALLOWED,

    // --- Données ---
    DUPLICATE_RESOURCE,
    DATABASE_ERROR,

    // --- Erreurs génériques ---
    BUSINESS_ERROR,
    INTERNAL_ERROR
}

