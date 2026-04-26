package com.nexilo.ai.summary.service;

import com.nexilo.ai.summary.dto.SummaryResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

/**
 * Service de résumé PDF par IA.
 */
public interface SummaryService {

    /**
     * Génère (ou retourne depuis le cache) le résumé d'un document PDF.
     *
     * <p>Idempotence : si le même PDF a déjà été traité (même SHA-256),
     * retourne le résumé existant sans rappeler l'IA.
     *
     * @param file   le fichier PDF uploadé
     * @param userId l'identifiant de l'utilisateur courant
     * @return le résumé structuré
     */
    SummaryResponse summarize(MultipartFile file, Long userId);

    /**
     * Récupère un résumé existant par l'ID du document.
     *
     * @param documentId l'UUID du document source
     * @return le résumé correspondant
     */
    SummaryResponse getByDocumentId(UUID documentId);
}

