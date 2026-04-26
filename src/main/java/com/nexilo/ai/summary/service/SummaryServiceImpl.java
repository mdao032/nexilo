package com.nexilo.ai.summary.service;

import com.nexilo.ai.summary.dto.SummaryMapper;
import com.nexilo.ai.summary.dto.SummaryResponse;
import com.nexilo.ai.summary.entity.Summary;
import com.nexilo.ai.summary.entity.SummaryDocument;
import com.nexilo.ai.summary.repository.SummaryDocumentRepository;
import com.nexilo.ai.summary.repository.SummaryRepository;
import com.nexilo.common.exception.ErrorCode;
import com.nexilo.common.exception.NexiloException;
import com.nexilo.processing.utils.PdfExtractor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import com.nexilo.user.entity.FeatureType;
import com.nexilo.user.quota.CheckQuota;
import com.nexilo.usage.UsageRecord;
import com.nexilo.usage.UsageService;
import com.nexilo.storage.FileStorageService;

import java.io.ByteArrayInputStream;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Implémentation du service de résumé PDF.
 *
 * <p>Pipeline :
 * <ol>
 *   <li>Validation du fichier (MIME, taille)</li>
 *   <li>Calcul du hash SHA-256 → idempotence</li>
 *   <li>Si résumé déjà existant : retour immédiat (cache)</li>
 *   <li>Extraction du texte via PDFBox</li>
 *   <li>Troncature intelligente si texte > 100 000 chars</li>
 *   <li>Appel Claude via Spring AI ChatClient (@Retryable)</li>
 *   <li>Sauvegarde en base et retour du résultat</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SummaryServiceImpl implements SummaryService {

    private static final String MIME_PDF = "application/pdf";
    private static final long MAX_FILE_SIZE = 20L * 1024 * 1024; // 20 MB
    private static final int MAX_TEXT_LENGTH = 100_000;
    private static final int INTRO_LENGTH = 60_000;
    private static final int CONCLUSION_LENGTH = 40_000;

    @Value("${spring.ai.anthropic.chat.options.model:claude-sonnet-4-5}")
    private String modelName;

    private final ChatClient chatClient;
    private final PdfExtractor pdfExtractor;
    private final SummaryDocumentRepository documentRepository;
    private final SummaryRepository summaryRepository;
    private final SummaryMapper summaryMapper;
    private final UsageService usageService;
    private final FileStorageService fileStorageService;

    // =========================================================================
    // Public API
    // =========================================================================

    @Override
    @Transactional
    @CheckQuota(feature = FeatureType.SUMMARY)
    public SummaryResponse summarize(MultipartFile file, Long userId) {
        validateFile(file);

        byte[] bytes = readBytes(file);
        String hash = computeSha256(bytes);

        // --- Idempotence : résumé déjà existant pour ce PDF ? ---
        return documentRepository.findByContentHash(hash)
                .flatMap(doc -> summaryRepository.findByDocumentId(doc.getId()))
                .map(existing -> {
                    log.info("Résumé trouvé en cache pour hash={}", hash.substring(0, 8));
                    return summaryMapper.toResponse(existing, true);
                })
                .orElseGet(() -> generateAndSave(file, bytes, hash, userId));
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = "summaries", key = "#documentId.toString()")
    public SummaryResponse getByDocumentId(UUID documentId) {
        Summary summary = summaryRepository.findByDocumentId(documentId)
                .orElseThrow(() -> new NexiloException(
                        ErrorCode.RESOURCE_NOT_FOUND,
                        HttpStatus.NOT_FOUND,
                        "Aucun résumé trouvé pour le document : " + documentId));
        return summaryMapper.toResponse(summary, true);
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    private SummaryResponse generateAndSave(MultipartFile file, byte[] bytes,
                                             String hash, Long userId) {
        // 1. Sauvegarder le document (sans storageKey pour l'instant)
        SummaryDocument doc = documentRepository.save(SummaryDocument.builder()
                .fileName(file.getOriginalFilename())
                .fileSize(file.getSize())
                .contentHash(hash)
                .userId(userId)
                .build());

        // 2. Stocker le fichier PDF (async ne fonctionne pas ici — on a besoin du docId)
        String storageKey = FileStorageService.buildKey(userId, doc.getId().toString(),
                file.getOriginalFilename() != null ? file.getOriginalFilename() : "document.pdf");
        String storageUrl = fileStorageService.store(file, storageKey);
        doc.setStorageKey(storageKey);
        doc.setStorageUrl(storageUrl);

        // 3. Extraire le texte PDF
        String text = extractText(bytes, doc.getId());

        // 4. Sauvegarder le texte extrait pour l'ingestion RAG ultérieure
        doc.setExtractedText(text);
        documentRepository.save(doc);

        // 4. Troncature intelligente si nécessaire
        String processedText = truncateIntelligently(text);

        // 5. Générer le résumé via Claude (avec retry)
        long t0 = System.currentTimeMillis();
        String content = callClaude(processedText);
        long responseTimeMs = System.currentTimeMillis() - t0;

        // 6. Détecter la langue (heuristique simple)
        String language = detectLanguage(content);

        // 7. Estimer les tokens (approximation : 1 token ≈ 4 chars)
        int tokensUsed = (processedText.length() + content.length()) / 4;

        // 8. Sauvegarder le résumé
        Summary summary = summaryRepository.save(Summary.builder()
                .document(doc)
                .content(content)
                .language(language)
                .model(modelName)
                .tokensUsed(tokensUsed)
                .build());

        log.info("Résumé généré — docId={}, {} chars, langue={}, ~{} tokens",
                doc.getId(), content.length(), language, tokensUsed);

        // 9. Tracking usage (async — ne bloque pas la réponse)
        usageService.recordUsage(UsageRecord.builder()
                .userId(userId)
                .feature(FeatureType.SUMMARY)
                .tokensUsed(tokensUsed)
                .costMicroUsd(UsageRecord.calculateCost(tokensUsed))
                .documentId(doc.getId())
                .responseTimeMs(responseTimeMs)
                .build());

        return summaryMapper.toResponse(summary, false);
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new NexiloException(ErrorCode.INVALID_FILE_TYPE, HttpStatus.BAD_REQUEST,
                    "Le fichier est requis et ne doit pas être vide");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new NexiloException(ErrorCode.FILE_TOO_LARGE, HttpStatus.PAYLOAD_TOO_LARGE,
                    String.format("Le fichier dépasse la limite de %d MB. Taille reçue : %.1f MB",
                            MAX_FILE_SIZE / (1024 * 1024), file.getSize() / (1024.0 * 1024.0)));
        }
        String contentType = file.getContentType();
        if (!MIME_PDF.equalsIgnoreCase(contentType)) {
            throw new NexiloException(ErrorCode.INVALID_FILE_TYPE, HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                    "Seuls les fichiers PDF sont acceptés. Type reçu : " + contentType);
        }
    }

    private byte[] readBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (Exception e) {
            throw new NexiloException(ErrorCode.PROCESSING_ERROR, HttpStatus.INTERNAL_SERVER_ERROR,
                    "Impossible de lire le fichier uploadé", e);
        }
    }

    private String computeSha256(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(bytes));
        } catch (Exception e) {
            throw new NexiloException(ErrorCode.PROCESSING_ERROR, HttpStatus.INTERNAL_SERVER_ERROR,
                    "Erreur de calcul du hash SHA-256", e);
        }
    }

    private String extractText(byte[] bytes, UUID docId) {
        try {
            String text = pdfExtractor.extract(new ByteArrayInputStream(bytes));
            if (text == null || text.isBlank()) {
                throw new NexiloException(ErrorCode.PROCESSING_ERROR, HttpStatus.UNPROCESSABLE_ENTITY,
                        "Le PDF ne contient pas de texte extractible (scan ou PDF protégé ?)");
            }
            log.info("Texte extrait du PDF docId={} — {} chars", docId, text.length());
            return text;
        } catch (NexiloException e) {
            throw e;
        } catch (Exception e) {
            throw new NexiloException(ErrorCode.PROCESSING_ERROR, HttpStatus.UNPROCESSABLE_ENTITY,
                    "Impossible d'extraire le texte du PDF : " + e.getMessage(), e);
        }
    }

    /**
     * Troncature intelligente : conserve l'introduction et la conclusion.
     * Si texte > 100 000 chars → garde les 60 000 premiers + les 40 000 derniers.
     */
    private String truncateIntelligently(String text) {
        if (text.length() <= MAX_TEXT_LENGTH) {
            return text;
        }
        log.info("Texte tronqué ({} chars) → intro {} + conclusion {}",
                text.length(), INTRO_LENGTH, CONCLUSION_LENGTH);
        return text.substring(0, INTRO_LENGTH)
                + "\n\n[... contenu central tronqué pour optimisation ...]\n\n"
                + text.substring(text.length() - CONCLUSION_LENGTH);
    }

    /**
     * Appelle Claude via Spring AI ChatClient avec prompt structuré.
     * Retry automatique (3 tentatives, backoff exponentiel) en cas d'erreur.
     */
    @Retryable(
            retryFor = NexiloException.class,
            maxAttempts = 3,
            backoff = @Backoff(delay = 2000, multiplier = 2.0, maxDelay = 30_000)
    )    public String callClaude(String text) {
        log.info("Appel Claude — {} chars de texte", text.length());
        try {
            String result = chatClient.prompt()
                    .user(u -> u.text("""
                            Analyze the following document and provide a structured response with these sections:

                            ## Executive Summary
                            (3-5 sentences summarizing the document's purpose and main message)

                            ## Key Points
                            (5-10 bullet points covering the most important information)

                            ## Main Themes
                            (List the 3-5 major themes or topics covered)

                            ## Language
                            (State the document's language as ISO 639-1 code, e.g., "fr", "en", "es")

                            Be concise, professional, and respond in the same language as the document.

                            Document:
                            {document}
                            """)
                            .param("document", text))
                    .call()
                    .content();

            if (result == null || result.isBlank()) {
                throw new NexiloException(ErrorCode.AI_SERVICE_ERROR, HttpStatus.SERVICE_UNAVAILABLE,
                        "Claude a retourné une réponse vide");
            }
            return result.trim();

        } catch (NexiloException e) {
            throw e;
        } catch (Exception e) {
            log.error("Erreur appel Claude : {}", e.getMessage());
            throw new NexiloException(ErrorCode.AI_SERVICE_ERROR, HttpStatus.SERVICE_UNAVAILABLE,
                    "Le service IA est temporairement indisponible : " + e.getMessage(), e);
        }
    }

    /**
     * Détection de la langue par recherche du marqueur dans la réponse Claude.
     * Retourne "unknown" si non trouvé.
     */
    private String detectLanguage(String content) {
        String lower = content.toLowerCase();
        int langIdx = lower.lastIndexOf("## language");
        if (langIdx == -1) langIdx = lower.lastIndexOf("## langue");
        if (langIdx != -1) {
            String after = content.substring(langIdx).lines()
                    .skip(1).findFirst().orElse("").trim().toLowerCase();
            if (!after.isBlank() && after.length() <= 5) return after;
        }
        // Heuristique : mots français communs
        if (lower.contains(" le ") || lower.contains(" la ") || lower.contains(" les ")) return "fr";
        if (lower.contains(" the ") || lower.contains(" is ") || lower.contains(" are ")) return "en";
        return "unknown";
    }
}

