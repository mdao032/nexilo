package com.nexilo.common.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration Spring AI pour Nexilo.
 *
 * <p>Fournit un {@link ChatClient} pré-configuré avec Anthropic Claude.
 * Le provider alternatif Gemini (Google) utilise une implémentation RestTemplate
 * distincte via {@link com.nexilo.ai.provider.GeminiService}, activable via
 * {@code nexilo.ai.active-provider=gemini}.
 *
 * <p>Le {@link ChatClient} Spring AI (Claude) est toujours actif pour les services
 * SummaryService, ExtractionService, QnaService qui requièrent les fonctions avancées
 * de Spring AI (streaming, RAG, etc.).
 */
@Slf4j
@Configuration
public class SpringAiConfig {

    private static final String NEXILO_SYSTEM_PROMPT = """
            You are Nexilo AI, an expert document analyst integrated into the Nexilo platform.
            Your role is to analyze documents (PDF, reports, contracts, etc.) and provide:
            - Clear, structured summaries in the same language as the document
            - Key insights and actionable information
            - Important points, risks, or opportunities identified

            Always be precise, professional, and concise.
            Format your response in readable sections when appropriate.
            If the document language is French, respond in French.
            """;

    /**
     * Bean {@link ChatClient} configuré avec Anthropic Claude et le system prompt Nexilo.
     * Utilisé par SummaryServiceImpl, ExtractionServiceImpl, QnaServiceImpl.
     *
     * @param chatModel le modèle Anthropic auto-configuré par Spring AI
     * @return un ChatClient prêt à l'emploi
     */
    @Bean
    public ChatClient chatClient(@Qualifier("anthropicChatModel") ChatModel chatModel) {
        log.info("⚡ Spring AI ChatClient initialisé avec Anthropic Claude");
        return ChatClient.builder(chatModel)
                .defaultSystem(NEXILO_SYSTEM_PROMPT)
                .build();
    }
}
