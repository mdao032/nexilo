package com.nexilo.ai.service;

/**
 * Interface définissant les capacités IA du système.
 * Chaque provider (Gemini, OpenAI, etc.) doit implémenter cette interface.
 */
public interface AiProviderService {

    /**
     * Génère un résumé structuré du texte fourni.
     *
     * @param text le texte extrait du document à analyser
     * @return le résumé généré, ou un message de fallback en cas d'échec
     */
    String summarize(String text);

    /**
     * Génère une réponse libre à partir d'un prompt.
     *
     * @param prompt le prompt à envoyer au modèle
     * @return la réponse générée
     */
    String generateResponse(String prompt);
}

