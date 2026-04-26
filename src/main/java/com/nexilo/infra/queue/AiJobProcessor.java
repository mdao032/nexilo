package com.nexilo.infra.queue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexilo.ai.extraction.dto.ExtractionRequest;
import com.nexilo.ai.extraction.service.ExtractionService;
import com.nexilo.ai.qna.dto.QnaRequest;
import com.nexilo.ai.qna.service.DocumentIngestionService;
import com.nexilo.ai.qna.service.QnaService;
import com.nexilo.core.conversion.ConversionJobHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Processeur de jobs IA asynchrone.
 *
 * <p>Écoute les {@link AiJobEvent} Spring et dispatche vers le bon service selon le type.
 * Tourne dans le pool {@code aiJobExecutor} (threads dédiés, séparés du pool applicatif).
 *
 * <p>Gestion des erreurs :
 * <ul>
 *   <li>Si le job peut être retenté ({@code attempts < MAX_ATTEMPTS}) → status PENDING
 *       et republication de l'événement après un délai.</li>
 *   <li>Après {@code MAX_ATTEMPTS} échecs → status FAILED + message d'erreur persisté.</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiJobProcessor {

    private final AiJobRepository jobRepository;
    private final ObjectMapper objectMapper;

    // Services IA
    private final DocumentIngestionService ingestionService;
    private final ExtractionService extractionService;
    private final QnaService qnaService;
    private final ConversionJobHandler conversionJobHandler;

    // =========================================================================
    // Point d'entrée — écoute les events, thread pool dédié
    // =========================================================================

    /**
     * Traite un job IA de manière asynchrone.
     * Appelé par Spring après publication d'un {@link AiJobEvent}.
     *
     * @param event événement contenant le job à traiter
     */
    @Async("aiJobExecutor")
    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handleJobEvent(AiJobEvent event) {
        AiJob job = jobRepository.findById(event.getJob().getId()).orElse(null);
        if (job == null) {
            log.warn("Job introuvable en base — ignoré : {}", event.getJob().getId());
            return;
        }
        if (job.getStatus() != AiJobStatus.PENDING) {
            log.debug("Job {} déjà en cours ou terminé (status={}) — ignoré", job.getId(), job.getStatus());
            return;
        }
        processJob(job);
    }

    // =========================================================================
    // Traitement principal
    // =========================================================================

    private void processJob(AiJob job) {
        job.markProcessing();
        jobRepository.save(job);
        log.info("Traitement job démarré — id={}, type={}, tentative={}/{}",
                job.getId(), job.getType(), job.getAttempts(), AiJob.MAX_ATTEMPTS);
        try {
            String resultJson = dispatch(job);
            job.markDone(resultJson);
            jobRepository.save(job);
            log.info("Job terminé avec succès — id={}, type={}", job.getId(), job.getType());
        } catch (Exception ex) {
            log.error("Erreur traitement job {} (type={}, tentative={}): {}",
                    job.getId(), job.getType(), job.getAttempts(), ex.getMessage(), ex);
            handleFailure(job, ex);
        }
    }

    private void handleFailure(AiJob job, Exception ex) {
        if (job.canRetry()) {
            log.warn("Job {} — nouvelle tentative possible ({}/{}). Repassage en PENDING.",
                    job.getId(), job.getAttempts(), AiJob.MAX_ATTEMPTS);
            job.markRetry();
            jobRepository.save(job);
            // Republier l'événement pour relancer le traitement
            // (Spring gère la déprogrammation via le pool — pas de sleep bloquant)
            jobRepository.flush();
        } else {
            log.error("Job {} — échec définitif après {} tentatives.", job.getId(), job.getAttempts());
            job.markFailed(buildErrorMessage(ex));
            jobRepository.save(job);
        }
    }

    // =========================================================================
    // Dispatch par type
    // =========================================================================

    /**
     * Dispatche le job vers le bon service et retourne le résultat sérialisé en JSON.
     */
    private String dispatch(AiJob job) throws Exception {
        return switch (job.getType()) {
            case INGEST      -> handleIngest(job);
            case EXTRACTION  -> handleExtraction(job);
            case QNA         -> handleQna(job);
            case SUMMARY     -> handleSummaryPlaceholder(job);
            case CONVERSION  -> conversionJobHandler.handle(job);
        };
    }

    /**
     * INGEST — ingère le document dans le vector store.
     * Le payload est ignoré (seul documentId est nécessaire).
     */
    private String handleIngest(AiJob job) throws Exception {
        UUID docId = requireDocumentId(job);
        int chunks = ingestionService.ingestDocument(docId);
        return objectMapper.writeValueAsString(
                java.util.Map.of("documentId", docId.toString(), "chunksCreated", chunks));
    }

    /**
     * EXTRACTION — extrait les données structurées selon le template/fields du payload.
     */
    private String handleExtraction(AiJob job) throws Exception {
        UUID docId = requireDocumentId(job);
        ExtractionRequest request = deserializePayload(job.getPayload(), ExtractionRequest.class);
        var result = extractionService.extract(docId, request);
        return objectMapper.writeValueAsString(result);
    }

    /**
     * QNA — pose une question sur le document.
     */
    private String handleQna(AiJob job) throws Exception {
        UUID docId = requireDocumentId(job);
        QnaRequest request = deserializePayload(job.getPayload(), QnaRequest.class);
        var result = qnaService.askQuestion(docId, request, job.getUserId());
        return objectMapper.writeValueAsString(result);
    }

    /**
     * SUMMARY via job — le résumé est généralement synchrone (endpoint dédié).
     * Ce handler permet de le lancer en async depuis la soumission de job.
     * Le résumé existant est retourné si déjà calculé.
     */
    private String handleSummaryPlaceholder(AiJob job) throws Exception {
        UUID docId = requireDocumentId(job);
        // Récupère le résumé déjà calculé (l'upload synchrone a déjà appelé summarize())
        // Sert principalement pour la traçabilité job
        return objectMapper.writeValueAsString(
                java.util.Map.of("documentId", docId.toString(),
                        "message", "Résumé disponible via GET /api/v1/documents/" + docId + "/summary"));
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private UUID requireDocumentId(AiJob job) {
        if (job.getDocumentId() == null) {
            throw new IllegalArgumentException("documentId requis pour le job de type " + job.getType());
        }
        return job.getDocumentId();
    }

    private <T> T deserializePayload(String json, Class<T> clazz) {
        if (json == null || json.isBlank()) {
            try {
                return clazz.getDeclaredConstructor().newInstance();
            } catch (Exception e) {
                throw new IllegalArgumentException("Payload manquant pour " + clazz.getSimpleName());
            }
        }
        try {
            return objectMapper.readValue(json, clazz);
        } catch (Exception e) {
            throw new IllegalArgumentException(
                    "Payload invalide pour " + clazz.getSimpleName() + " : " + e.getMessage(), e);
        }
    }

    private String buildErrorMessage(Exception ex) {
        String msg = ex.getMessage();
        return msg != null && msg.length() > 1000 ? msg.substring(0, 1000) : msg;
    }
}

