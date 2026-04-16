package com.nexilo.ai.provider;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.nexilo.ai.dto.AiRequestDto;
import com.nexilo.ai.dto.AiResponseDto;
import com.nexilo.ai.service.AiService;
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

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Implémentation du service AI utilisant l'API Google Gemini.
 * Gère la génération de résumés de documents via le modèle Gemini configuré.
 * Supporte le retry automatique en cas de rate limit (429) ou d'erreur serveur (5xx).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GeminiService implements AiService {

    private static final String GEMINI_API_BASE_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent?key=%s";
    private static final int MAX_TEXT_LENGTH = 12000;
    /** Délais de retry en ms : 15s → 30s → 60s */
    private static final long[] RETRY_DELAYS_MS = {15_000, 30_000, 60_000};

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
                    return "AI summary temporarily unavailable";
                }

            } catch (HttpClientErrorException e) {
                log.error("Gemini erreur client HTTP {} (tentative {}) — body : {}",
                        e.getStatusCode(), attempt + 1, e.getResponseBodyAsString());
                return "AI summary temporarily unavailable";

            } catch (HttpServerErrorException e) {
                if (attempt < RETRY_DELAYS_MS.length) {
                    long wait = RETRY_DELAYS_MS[attempt];
                    log.warn("Gemini erreur 5xx [{}] (tentative {}). Retry dans {} secondes. body={}",
                            e.getStatusCode(), attempt + 1, wait / 1000, safeBody(e));
                    sleepSilently(wait);
                } else {
                    log.error("Gemini 5xx persistant après {} tentatives.", attempt + 1);
                    return "AI summary temporarily unavailable";
                }

            } catch (RestClientException e) {
                log.error("Gemini RestClientException (tentative {}) : {}", attempt + 1, e.getMessage());
                return "AI summary temporarily unavailable";

            } catch (Exception e) {
                log.error("Erreur inattendue Gemini summarize (tentative {})", attempt + 1, e);
                return "AI summary temporarily unavailable";
            }
        }
        return "AI summary temporarily unavailable";
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
            return "AI summary temporarily unavailable";
        }

        if (response.candidates == null || response.candidates.isEmpty()) {
            // Gemini peut bloquer la réponse (safety filter) et retourner candidates vide
            log.warn("Gemini : aucun candidat dans la réponse. promptFeedback={}", response.promptFeedback);
            return "AI summary temporarily unavailable";
        }

        Candidate first = response.candidates.get(0);

        if (first == null || first.content == null
                || first.content.parts == null || first.content.parts.isEmpty()) {
            log.warn("Gemini : contenu du candidat vide. finishReason={}", first != null ? first.finishReason : "null");
            return "AI summary temporarily unavailable";
        }

        String content = first.content.parts.get(0).text;
        return Objects.requireNonNullElse(content, "AI summary temporarily unavailable").trim();
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

    // --- Méthodes AiService non utilisées par le pipeline de summarization ---

    @Override
    public String generateResponse(String prompt) {
        return summarize(prompt);
    }

    @Override
    public AiResponseDto processRequest(AiRequestDto requestDto) {
        return null;
    }

    @Override
    public List<AiResponseDto> getAllRequests() {
        return Collections.emptyList();
    }

    @Override
    public AiResponseDto getRequest(Long id) {
        return null;
    }

    // =========================================================================
    // DTOs internes pour la sérialisation JSON de l'API Gemini
    // =========================================================================

    /** Corps de la requête envoyée à l'API Gemini. */
    public record GeminiRequest(List<Content> contents) {}

    /** Contenu structuré d'un tour de conversation (rôle + parties). */
    public record Content(String role, List<Part> parts) {}

    /** Fragment de texte dans un contenu. */
    public record Part(String text) {}

    /** Réponse racine retournée par l'API Gemini. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class GeminiResponse {
        public List<Candidate> candidates;
        public Object promptFeedback;
    }

    /** Candidat de réponse généré par le modèle. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Candidate {
        public ContentResponse content;
        public String finishReason;
    }

    /** Contenu de la réponse d'un candidat. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ContentResponse {
        public List<PartResponse> parts;
        public String role;
    }

    /** Fragment de texte dans la réponse. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PartResponse {
        public String text;
    }
}

