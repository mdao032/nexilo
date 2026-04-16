package com.nexilo.ai.provider;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.nexilo.ai.service.AiProviderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClientException;
import java.util.List;
import org.springframework.web.client.RestTemplate;

import java.util.Objects;

/**
 * Ancien provider OpenAI - désactivé au profit de GeminiService.
 * Conserver pour référence ou réactivation future.
 * Pour réactiver : remettre @Service et retirer @Service de GeminiService.
 */
@Slf4j
@RequiredArgsConstructor
public class OpenAIService implements AiProviderService {

    private static final String OPENAI_API_URL = "https://api.openai.com/v1/chat/completions";
    private static final String MODEL = "gpt-4o-mini";

    private final RestTemplate restTemplate;

    @Value("${openai.api-key}")
    private String apiKey;

    @Override
    public String summarize(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }

        String processedText = text.length() > 12000 ? text.substring(0, 12000) : text;

        String prompt = "You are an expert document analyst.\n" +
                "Analyze the following document and provide:\n\n" +
                "1. A clear and concise summary\n" +
                "2. Key insights\n" +
                "3. Important points\n\n" +
                "Document:\n" + processedText;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        ChatCompletionsRequest requestBody = new ChatCompletionsRequest(
                MODEL,
                List.of(new Message("user", prompt)),
                0.3
        );

        HttpEntity<ChatCompletionsRequest> requestEntity = new HttpEntity<>(requestBody, headers);

        try {
            return callOpenAiOnce(requestEntity);
        } catch (org.springframework.web.client.HttpClientErrorException.TooManyRequests e) {
            // 429: retry once after 2 seconds
            sleepSilently(2000);
            try {
                return callOpenAiOnce(requestEntity);
            } catch (Exception ex) {
                log.error("OpenAI call failed after retry (429).", ex);
                return "AI summary temporarily unavailable";
            }
        } catch (HttpServerErrorException e) {
            // 5xx: retry once after 2 seconds
            log.warn("OpenAI 5xx error. Retrying once. status={}, body={}", e.getStatusCode(), safeBody(e));
            sleepSilently(2000);
            try {
                return callOpenAiOnce(requestEntity);
            } catch (Exception ex) {
                log.error("OpenAI call failed after retry (5xx).", ex);
                return "AI summary temporarily unavailable";
            }
        } catch (RestClientException e) {
            log.error("OpenAI client error", e);
            return "AI summary temporarily unavailable";
        } catch (Exception e) {
            log.error("Unexpected error during OpenAI summarization", e);
            return "AI summary temporarily unavailable";
        }
    }

    private String callOpenAiOnce(HttpEntity<ChatCompletionsRequest> requestEntity) {
        ResponseEntity<ChatCompletionsResponse> responseEntity = restTemplate.postForEntity(
                OPENAI_API_URL,
                requestEntity,
                ChatCompletionsResponse.class
        );

        ChatCompletionsResponse response = responseEntity.getBody();
        if (response == null || response.choices == null || response.choices.isEmpty()) {
            return "AI summary temporarily unavailable";
        }

        String content = response.choices.getFirst() != null && response.choices.getFirst().message != null
                ? response.choices.getFirst().message.content
                : null;

        return Objects.requireNonNullElse(content, "AI summary temporarily unavailable").trim();
    }

    private static void sleepSilently(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    private static String safeBody(HttpServerErrorException e) {
        try {
            return e.getResponseBodyAsString();
        } catch (Exception ex) {
            return "";
        }
    }

    // --- Méthodes AiService non utilisées par le pipeline actuel ---

    @Override
    public String generateResponse(String prompt) {
        return "Not implemented in OpenAIService";
    }




    // --- DTOs pour l'API OpenAI (Jackson) ---

    public record ChatCompletionsRequest(String model, List<Message> messages, Double temperature) {
    }

    public record Message(String role, String content) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ChatCompletionsResponse {
        public List<Choice> choices;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Choice {
        public Message message;
    }
}
