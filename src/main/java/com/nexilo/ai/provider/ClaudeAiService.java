package com.nexilo.ai.provider;

import com.nexilo.ai.service.AiProviderService;
import com.nexilo.common.exception.ErrorCode;
import com.nexilo.common.exception.NexiloException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * Provider IA basé sur Spring AI + Anthropic Claude.
 *
 * <p>Actif par défaut (quand {@code nexilo.ai.active-provider} vaut {@code claude}
 * ou est absent). Marqué {@link Primary} sauf si Gemini est sélectionné.
 */
@Slf4j
@Service
@Primary
@ConditionalOnProperty(name = "nexilo.ai.active-provider", havingValue = "claude", matchIfMissing = true)
@RequiredArgsConstructor
public class ClaudeAiService implements AiProviderService {

    private static final int MAX_TEXT_LENGTH = 12_000;

    private final ChatClient chatClient;

    /**
     * Génère un résumé structuré en 3 sections via Claude.
     * Tronque le texte au-delà de {@value MAX_TEXT_LENGTH} caractères.
     */
    @Override
    public String summarize(String text) {
        if (text == null || text.isBlank()) {
            log.warn("ClaudeAiService.summarize : texte vide, skip.");
            return "";
        }

        String truncated = text.length() > MAX_TEXT_LENGTH
                ? text.substring(0, MAX_TEXT_LENGTH) + "\n[... texte tronqué]"
                : text;

        log.info("ClaudeAiService.summarize — {} chars envoyés à Claude", truncated.length());

        try {
            String result = chatClient.prompt()
                    .user(u -> u
                            .text("Analyze the following document and provide:\n\n"
                                    + "1. **Summary** — A clear and concise overview\n"
                                    + "2. **Key Insights** — The most important findings\n"
                                    + "3. **Important Points** — Action items, risks, or highlights\n\n"
                                    + "Document:\n{document}")
                            .param("document", truncated))
                    .call()
                    .content();

            log.info("ClaudeAiService.summarize OK — {} chars générés",
                    result != null ? result.length() : 0);
            return result != null ? result.trim() : "Le résumé n'a pas pu être généré.";

        } catch (Exception e) {
            log.error("ClaudeAiService.summarize erreur — {}", e.getMessage(), e);
            throw new NexiloException(
                    ErrorCode.AI_SERVICE_ERROR,
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Le service IA Claude est indisponible : " + e.getMessage(),
                    e
            );
        }
    }

    /**
     * Génère une réponse libre à partir d'un prompt.
     */
    @Override
    public String generateResponse(String prompt) {
        if (prompt == null || prompt.isBlank()) return "";

        log.info("ClaudeAiService.generateResponse — {} chars", prompt.length());

        try {
            String result = chatClient.prompt()
                    .user(prompt)
                    .call()
                    .content();
            return result != null ? result.trim() : "";

        } catch (Exception e) {
            log.error("ClaudeAiService.generateResponse erreur — {}", e.getMessage(), e);
            throw new NexiloException(
                    ErrorCode.AI_SERVICE_ERROR,
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Le service IA Claude est indisponible : " + e.getMessage(),
                    e
            );
        }
    }
}

