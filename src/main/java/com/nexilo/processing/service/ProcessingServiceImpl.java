package com.nexilo.processing.service;

import com.nexilo.document.entity.ProcessedResult;
import com.nexilo.processing.dto.ProcessingMapper;
import com.nexilo.processing.dto.ProcessingRequest;
import com.nexilo.processing.dto.ProcessingResponse;
import com.nexilo.processing.dto.ProcessingResultResponse;
import com.nexilo.processing.entity.ProcessingJob;
import com.nexilo.processing.repository.ProcessedResultRepository;
import com.nexilo.processing.repository.ProcessingRepository;
import com.nexilo.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Implémentation du service de gestion des jobs de traitement.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProcessingServiceImpl implements ProcessingService {

    private final ProcessingRepository processingRepository;
    private final ProcessingMapper processingMapper;
    private final ProcessingOrchestrator orchestrator;
    private final ProcessedResultRepository processedResultRepository;

    @Override
    @Transactional
    public ProcessingResponse createJob(ProcessingRequest request) {
        ProcessingJob job = processingMapper.toEntity(request);
        job.setStatus(ProcessingJob.JobStatus.PENDING);
        job = processingRepository.save(job);

        try {
            orchestrator.process(job, request.getFile().getInputStream());
        } catch (Exception e) {
            job.setStatus(ProcessingJob.JobStatus.FAILED);
            processingRepository.save(job);
            throw new RuntimeException("Echec du demarrage du traitement", e);
        }

        return processingMapper.toResponse(job);
    }

    @Override
    @Transactional(readOnly = true)
    public ProcessingResponse getJob(Long id) {
        ProcessingJob job = processingRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Processing Job not found: " + id));
        return processingMapper.toResponse(job);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProcessingResponse> getAllJobs() {
        return processingRepository.findAll().stream()
                .map(processingMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public ProcessingResponse updateJobStatus(Long id, String status) {
        ProcessingJob job = processingRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Processing Job not found: " + id));
        try {
            job.setStatus(ProcessingJob.JobStatus.valueOf(status.toUpperCase()));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Statut invalide: " + status);
        }
        return processingMapper.toResponse(processingRepository.save(job));
    }

    @Override
    public ProcessingResultResponse getResult(Long jobId) {
        ProcessingJob job = processingRepository.findById(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("Processing Job not found: " + jobId));

        if (job.getStatus() != ProcessingJob.JobStatus.COMPLETED) {
            return ProcessingResultResponse.builder()
                    .jobId(jobId)
                    .status(job.getStatus().name())
                    .summary(null)
                    .build();
        }

        ProcessedResult result = processedResultRepository.findByJobId(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("Result not found for job: " + jobId));

        return ProcessingResultResponse.builder()
                .jobId(jobId)
                .status(job.getStatus().name())
                .summary(result.getSummary())
                .build();
    }
}
