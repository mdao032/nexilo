package com.nexilo.infra.queue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexilo.common.exception.ErrorCode;
import com.nexilo.common.exception.NexiloException;
import com.nexilo.infra.queue.dto.JobStatusResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Implémentation du service de soumission et de consultation des jobs IA.
 *
 * <p>Le traitement effectif est délégué à {@link AiJobProcessor} via un
 * {@link AiJobEvent} Spring, ce qui découple totalement la soumission du traitement.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiJobServiceImpl implements AiJobService {

    private final AiJobRepository jobRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;

    // =========================================================================
    // Public API
    // =========================================================================

    @Override
    @Transactional
    public UUID submitJob(AiJobType type, UUID documentId, Long userId, Object payload) {
        String payloadJson = serializePayload(payload);

        AiJob job = jobRepository.save(AiJob.builder()
                .type(type)
                .documentId(documentId)
                .userId(userId)
                .payload(payloadJson)
                .build());

        log.info("Job soumis — id={}, type={}, documentId={}, userId={}", job.getId(), type, documentId, userId);

        // Publier l'événement APRÈS le commit de la transaction
        eventPublisher.publishEvent(new AiJobEvent(this, job));

        return job.getId();
    }

    @Override
    @Transactional(readOnly = true)
    public JobStatusResponse getJobStatus(UUID jobId, Long userId) {
        AiJob job = jobRepository.findByIdAndUserId(jobId, userId)
                .orElseThrow(() -> new NexiloException(
                        ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND,
                        "Job introuvable : " + jobId));
        return JobStatusResponse.from(job);
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private String serializePayload(Object payload) {
        if (payload == null) return null;
        if (payload instanceof String s) return s;
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            throw new NexiloException(ErrorCode.PROCESSING_ERROR, HttpStatus.INTERNAL_SERVER_ERROR,
                    "Impossible de sérialiser le payload du job", e);
        }
    }
}

