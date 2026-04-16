package com.nexilo.processing.service;

import com.nexilo.document.entity.ProcessedResult;
import com.nexilo.processing.entity.ProcessingJob;
import com.nexilo.processing.entity.ProcessingJob.JobStatus;
import com.nexilo.processing.entity.ProcessingJob.JobType;
import com.nexilo.processing.repository.ProcessedResultRepository;
import com.nexilo.processing.repository.ProcessingRepository;
import com.nexilo.processing.service.strategy.ProcessingStrategy;
import com.nexilo.processing.utils.PdfExtractor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Orchestrateur du pipeline de traitement de documents.
 * Selectionne dynamiquement la strategie adaptee au type de job
 * et execute le traitement de maniere asynchrone.
 */
@Slf4j
@Service
public class ProcessingOrchestrator {

    private final PdfExtractor extractor;
    private final Map<JobType, ProcessingStrategy> strategies;
    private final ProcessedResultRepository resultRepo;
    private final ProcessingRepository jobRepo;

    public ProcessingOrchestrator(
            PdfExtractor extractor,
            List<ProcessingStrategy> strategyList,
            ProcessedResultRepository resultRepo,
            ProcessingRepository jobRepo
    ) {
        this.extractor = extractor;
        this.resultRepo = resultRepo;
        this.jobRepo = jobRepo;
        this.strategies = strategyList.stream()
                .collect(Collectors.toMap(ProcessingStrategy::getSupportedType, s -> s));
        log.info("ProcessingOrchestrator initialise avec {} strategie(s): {}",
                strategies.size(), strategies.keySet());
    }

    /**
     * Traite un document de facon asynchrone :
     * extraction du texte, selection de la strategie, execution, sauvegarde du resultat.
     *
     * @param job        le job de traitement
     * @param fileStream le flux du fichier PDF
     */
    @Async("processingExecutor")
    public void process(ProcessingJob job, InputStream fileStream) {
        try {
            job.setStatus(JobStatus.PROCESSING);
            jobRepo.save(job);

            String text = extractor.extract(fileStream);
            if (text == null || text.isBlank()) {
                throw new RuntimeException("PDF vide ou illisible");
            }

            ProcessingStrategy strategy = strategies.get(job.getJobType());
            if (strategy == null) {
                throw new IllegalArgumentException("Aucune strategie pour le type: " + job.getJobType());
            }

            String result = strategy.process(text);

            resultRepo.save(ProcessedResult.builder()
                    .job(job)
                    .summary(result)
                    .build());

            job.setStatus(JobStatus.COMPLETED);
            jobRepo.save(job);
            log.info("Job {} COMPLETED avec succes (type={})", job.getId(), job.getJobType());

        } catch (Exception e) {
            job.setStatus(JobStatus.FAILED);
            jobRepo.save(job);
            log.error("Job {} FAILED (type={}): {}", job.getId(), job.getJobType(), e.getMessage(), e);
        }
    }
}
