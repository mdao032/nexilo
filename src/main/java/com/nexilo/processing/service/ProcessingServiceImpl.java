package com.nexilo.processing.service;

import com.nexilo.document.entity.ProcessedResult;
import com.nexilo.processing.dto.ProcessingMapper;
import com.nexilo.processing.dto.ProcessingResultResponse;
import com.nexilo.processing.entity.ProcessingJob;
import com.nexilo.processing.repository.ProcessedResultRepository;
import com.nexilo.processing.repository.ProcessingRepository;
import com.nexilo.processing.dto.ProcessingRequest;
import com.nexilo.processing.dto.ProcessingResponse;
import com.nexilo.common.exception.ResourceNotFoundException;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProcessingServiceImpl implements ProcessingService {

    private final ProcessingRepository processingRepository;
    private final ProcessingMapper processingMapper;
    private final DocumentProcessingService documentProcessingService;
    private final ProcessedResultRepository processedResultRepository;

    /**
     * Crée un job en base de données avec le statut PENDING et lance le traitement asynchrone.
     * En cas d'échec du démarrage, marque le job comme FAILED.
     *
     * @param request la requête de traitement
     * @return la réponse avec les détails du job
     */
    @Override
    @Transactional
    public ProcessingResponse createJob(ProcessingRequest request) {

        // 🔄 Création du job
        ProcessingJob job = processingMapper.toEntity(request);
        job.setStatus(ProcessingJob.JobStatus.PENDING);

        job = processingRepository.save(job);

        // 🚀 Lancement du traitement async
        try {
            documentProcessingService.process(job, request.getFile().getInputStream());
        } catch (Exception e) {
            job.setStatus(ProcessingJob.JobStatus.FAILED);
            processingRepository.save(job);
            throw new RuntimeException("Failed to start processing", e);
        }

        return processingMapper.toResponse(job);
    }

    /**
     * Récupère un job depuis la base de données via son ID.
     * Lance une exception si le job n'existe pas.
     *
     * @param id l'identifiant du job
     * @return le job mappé en DTO
     */
    @Override
    @Transactional(readOnly = true)
    public ProcessingResponse getJob(Long id) {
        ProcessingJob job = processingRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Processing Job not found with id: " + id));

        return processingMapper.toResponse(job);
    }

    /**
     * Récupère l'ensemble des jobs de traitement en base.
     *
     * @return la liste de tous les jobs au format DTO
     */
    @Override
    @Transactional(readOnly = true)
    public List<ProcessingResponse> getAllJobs() {
        return processingRepository.findAll().stream()
                .map(processingMapper::toResponse)
                .collect(Collectors.toList());
    }

    /**
     * Met à jour uniquement le statut d'un job de traitement existant.
     *
     * @param id l'identifiant du job à modifer
     * @param status la chaîne représentant le nouveau statut (doit correspondre à l'enum JobStatus)
     * @return le job mis à jour
     */
    @Override
    @Transactional
    public ProcessingResponse updateJobStatus(Long id, String status) {

        ProcessingJob job = processingRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Processing Job not found with id: " + id));

        try {
            job.setStatus(ProcessingJob.JobStatus.valueOf(status.toUpperCase()));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid status: " + status);
        }

        job = processingRepository.save(job);

        return processingMapper.toResponse(job);
    }

    public ProcessingResultResponse getResult(Long jobId) {

        ProcessingJob job = processingRepository.findById(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("Processing Job not found with id: " + jobId));

        // Si pas encore terminé
        if (job.getStatus() != ProcessingJob.JobStatus.COMPLETED) {
            return ProcessingResultResponse.builder()
                    .jobId(jobId)
                    .status(job.getStatus().name())
                    .summary(null)
                    .build();
        }

        ProcessedResult processedResult = processedResultRepository.findByJobId(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("Processed result not found for job id: " + jobId));

        return ProcessingResultResponse.builder()
                .jobId(jobId)
                .status(job.getStatus().name())
                .summary(processedResult.getSummary())
                .build();
    }
}