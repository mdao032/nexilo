package com.nexilo.ai.provider;

import com.nexilo.ai.service.AiProviderService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.List;

/**
 * Implémentation du service AI utilisant l'API Google Gemini.
 * Gère la génération de résumés de documents via le modèle Gemini configuré.
 * Supporte le retry automatique en cas de rate limit (429) ou d'erreur serveur (5xx).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GeminiService implements AiProviderService {

    private static final String GEMINI_API_BASE_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent?key=%s";
    private static final int MAX_TEXT_LENGTH = 12000;
    /** Délais de retry en ms : 3s → 6s (fail fast pour ne pas bloquer le thread) */
    private static final long[] RETRY_DELAYS_MS = {3_000, 6_000};

    private final RestTemplate restTemplate;

    @Value("${google.ai.api-key}")
    private String apiKey;

    /**
     * Modèle Gemini à utiliser. Valeur par défaut : gemini-2.5-flash.
     * Peut être surchargé dans application-dev.yml via google.ai.model.
     */
    @Value("${google.ai.model:gemini-2.5-flash}")
    private String model;

    /**
     * Vérifie au démarrage que la configuration Gemini est valide.
     * Logue un avertissement si la clé API semble absente ou est un placeholder.
     */
    @PostConstruct
    public void validateConfig() {
        if (apiKey == null || apiKey.isBlank() || apiKey.startsWith("VOTRE_")) {
            log.warn("⚠️  Clé API Google Gemini non configurée ! Définissez google.ai.api-key dans application-dev.yml");
        } else {
            log.info("✅ GeminiService prêt — modèle : [{}], clé : [{}***]",
                    model, apiKey.substring(0, Math.min(8, apiKey.length())));
        }
    }

    /**
     * Génère un résumé structuré du texte fourni via l'API Gemini.
     * Tronque automatiquement le texte si sa taille dépasse MAX_TEXT_LENGTH.
     * Effectue un retry unique en cas de 429 ou 5xx.
     *
     * @param text le texte extrait du document à analyser
     * @return le résumé généré, ou un message de fallback en cas d'échec
     */
    @Override
    public String summarize(String text) {
        if (text == null || text.isBlank()) {
            log.warn("Gemini summarize : texte vide ou null, skip.");
            return "";
        }

        String processedText = text.length() > MAX_TEXT_LENGTH
                ? text.substring(0, MAX_TEXT_LENGTH)
                : text;

        log.info("Gemini summarize — modèle=[{}], texte={} chars", model, processedText.length());

        String prompt = "You are an expert document analyst.\n" +
                "Analyze the following document and provide:\n\n" +
                "1. A clear and concise summary\n" +
                "2. Key insights\n" +
                "3. Important points\n\n" +
                "Document:\n" + processedText;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        GeminiRequest requestBody = new GeminiRequest(
                List.of(new Content("user", List.of(new Part(prompt))))
        );

        HttpEntity<GeminiRequest> requestEntity = new HttpEntity<>(requestBody, headers);
        String url = String.format(GEMINI_API_BASE_URL, model, apiKey);

        // Tentative initiale + retries avec backoff exponentiel
        for (int attempt = 0; attempt <= RETRY_DELAYS_MS.length; attempt++) {
            try {
                String result = callGeminiOnce(requestEntity, url);
                log.info("Gemini summarize OK (tentative {}) — {} chars générés", attempt + 1, result.length());
                return result;

            } catch (HttpClientErrorException.TooManyRequests e) {
                if (attempt < RETRY_DELAYS_MS.length) {
                    long wait = RETRY_DELAYS_MS[attempt];
                    log.warn("Gemini 429 rate limit (tentative {}). Retry dans {} secondes...", attempt + 1, wait / 1000);
                    sleepSilently(wait);
                } else {
                    log.error("Gemini 429 persistant après {} tentatives. Abandon.", attempt + 1);
                    throw new GeminiRateLimitException("Quota Gemini épuisé (429). Réessayez plus tard.");
                }

            } catch (HttpClientErrorException e) {
                String body = e.getResponseBodyAsString();
                log.error("Gemini erreur client HTTP {} (tentative {}) — URL modele=[{}] body : {}",
                        e.getStatusCode(), attempt + 1, model, body);
                throw new GeminiApiException("Gemini erreur API [" + e.getStatusCode() + "]: " + extractErrorMessage(body));

            } catch (HttpServerErrorException e) {
                String body = safeBody(e);
                log.error("Gemini erreur serveur HTTP {} (tentative {}) — modele=[{}] body : {}",
                        e.getStatusCode(), attempt + 1, model, body);
                if (attempt < RETRY_DELAYS_MS.length) {
                    long wait = RETRY_DELAYS_MS[attempt];
                    log.warn("Retry dans {} secondes...", wait / 1000);
                    sleepSilently(wait);
                } else {
                    throw new GeminiApiException("Gemini erreur serveur [" + e.getStatusCode() + "]: " + body);
                }

            } catch (RestClientException e) {
                log.error("Gemini RestClientException (tentative {}) modele=[{}] : {}", attempt + 1, model, e.getMessage());
                throw new GeminiApiException("Gemini erreur réseau: " + e.getMessage());

            } catch (GeminiRateLimitException | GeminiApiException e) {
                throw e; // propager sans wrapper

            } catch (Exception e) {
                log.error("Erreur inattendue Gemini summarize (tentative {}) modele=[{}]", attempt + 1, model, e);
                throw new GeminiApiException("Erreur inattendue: " + e.getMessage());
            }
        }
        throw new GeminiApiException("Gemini : toutes les tentatives ont échoué.");
    }

    /**
     * Effectue un appel unique à l'API Gemini et extrait le texte généré.
     *
     * @param requestEntity la requête HTTP encapsulée
     * @param url           l'URL complète incluant le modèle et la clé API
     * @return le texte généré par Gemini
     */
    private String callGeminiOnce(HttpEntity<GeminiRequest> requestEntity, String url) {
        ResponseEntity<GeminiResponse> responseEntity = restTemplate.postForEntity(
                url,
                requestEntity,
                GeminiResponse.class
        );

        GeminiResponse response = responseEntity.getBody();

        if (response == null) {
            log.warn("Gemini : corps de réponse null.");
            throw new GeminiApiException("Réponse Gemini vide.");
        }

        if (response.candidates == null || response.candidates.isEmpty()) {
            log.warn("Gemini : aucun candidat dans la réponse. promptFeedback={}", response.promptFeedback);
            throw new GeminiApiException("Gemini a retourné une réponse vide (safety filter ou quota). promptFeedback=" + response.promptFeedback);
        }

        Candidate first = response.candidates.getFirst();

        if (first == null || first.content == null
                || first.content.parts == null || first.content.parts.isEmpty()) {
            log.warn("Gemini : contenu du candidat vide. finishReason={}", first != null ? first.finishReason : "null");
            throw new GeminiApiException("Gemini : contenu vide. finishReason=" + (first != null ? first.finishReason : "null"));
        }

        String content = first.content.parts.getFirst().text;
        if (content == null || content.isBlank()) {
            throw new GeminiApiException("Gemini : texte généré vide.");
        }
        return content.trim();
    }

    /**
     * Suspend silencieusement le thread courant pendant la durée spécifiée.
     */
    private static void sleepSilently(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Extrait le corps de la réponse d'une erreur HTTP serveur sans lever d'exception.
     */
    private static String safeBody(HttpServerErrorException e) {
        try {
            return e.getResponseBodyAsString();
        } catch (Exception ex) {
            return "";
        }
    }

    // --- generateResponse delegue a summarize ---
    @Override
    public String generateResponse(String prompt) {
        return summarize(prompt);
    }

    /**
     * Extrait le message d'erreur d'une réponse JSON Gemini, ou retourne le texte brut.
     */
    private static String extractErrorMessage(String body) {
        if (body == null) return "";
        try {
            int msgIdx = body.indexOf("\"message\"");
            if (msgIdx >= 0) {
                int start = body.indexOf("\"", msgIdx + 10) + 1;
                int end = body.indexOf("\"", start);
                return body.substring(start, end);
            }
        } catch (Exception ignored) {}
        return body.length() > 200 ? body.substring(0, 200) : body;
    }

    /** Exception levée en cas de rate limit Gemini (429). */
    public static class GeminiRateLimitException extends RuntimeException {
        public GeminiRateLimitException(String message) { super(message); }
    }

    /** Exception levée en cas d'erreur générale de l'API Gemini. */
    public static class GeminiApiException extends RuntimeException {
        public GeminiApiException(String message) { super(message); }
    }
    // =========================================================================
    // DTOs internes pour la serialisation JSON de l'API Gemini
    // =========================================================================
    public record GeminiRequest(java.util.List<Content> contents) {}
    public record Content(String role, java.util.List<Part> parts) {}
    public record Part(String text) {}
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
    public static class GeminiResponse {
        public java.util.List<Candidate> candidates;
        public Object promptFeedback;
    }
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
    public static class Candidate {
        public ContentResponse content;
        public String finishReason;
    }
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
    public static class ContentResponse {
        public java.util.List<PartResponse> parts;
    }
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
    public static class PartResponse {
        public String text;
    }
}
