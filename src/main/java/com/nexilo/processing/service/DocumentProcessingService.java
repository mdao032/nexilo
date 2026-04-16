package com.nexilo.processing.service;

import com.nexilo.ai.service.AiService;
import com.nexilo.document.entity.ProcessedResult;
import com.nexilo.processing.entity.ProcessingJob;
import com.nexilo.processing.repository.ProcessedResultRepository;
import com.nexilo.processing.repository.ProcessingRepository;
import com.nexilo.processing.utils.PdfExtractor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.lang.invoke.MethodHandles;

@Service
public class DocumentProcessingService {

    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private final PdfExtractor extractor;
    private final AiService aiService;
    private final ProcessedResultRepository processedResultRepository;
    private final ProcessingRepository processingRepository;

    public DocumentProcessingService(
            PdfExtractor extractor,
            AiService aiService,
            ProcessedResultRepository processedResultRepository,
            ProcessingRepository processingRepository
    ) {
        this.extractor = extractor;
        this.aiService = aiService;
        this.processedResultRepository = processedResultRepository;
        this.processingRepository = processingRepository;
    }

    /**
     * Traite un document de manière asynchrone :
     * met à jour le statut, extrait le texte, génère un résumé via le service AI,
     * puis sauvegarde le résultat et marque le document comme terminé.
     * En cas d'erreur, le document passe au statut d'échec.
     *
     * @param job le job encapsulant le contexte de traitement
     * @param fileStream le flux de données correspondant au fichier à analyser
     */
    @Async("processingExecutor")
    public void process(ProcessingJob job, InputStream fileStream) {

        try {
            // 🔄 Update status → PROCESSING
            job.setStatus(ProcessingJob.JobStatus.PROCESSING);
            processingRepository.save(job);

            // 📄 Extraction texte
            String text = extractor.extract(fileStream);

            if (text == null || text.isBlank()) {
                throw new RuntimeException("Empty or unreadable PDF");
            }

            // 🤖 Appel IA
            String summary = aiService.summarize(text);

            // 💾 Sauvegarde résultat
            ProcessedResult result = ProcessedResult.builder()
                    .job(job)
                    .summary(summary)
                    .build();

            processedResultRepository.save(result);

            // ✅ Update status → COMPLETED
            job.setStatus(ProcessingJob.JobStatus.COMPLETED);
            processingRepository.save(job);

        } catch (Exception e) {

            // ❌ En cas d’erreur
            job.setStatus(ProcessingJob.JobStatus.FAILED);
            processingRepository.save(job);

            // log conseillé
            logger.error(e.getMessage(), e);
        }
    }
}
