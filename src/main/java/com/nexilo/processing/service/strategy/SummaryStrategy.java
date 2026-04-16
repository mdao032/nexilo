package com.nexilo.processing.service.strategy;

import com.nexilo.ai.service.AiProviderService;
import com.nexilo.processing.entity.ProcessingJob.JobType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Strategie de traitement : generation d'un resume via le service IA.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SummaryStrategy implements ProcessingStrategy {

    private final AiProviderService aiProviderService;

    @Override
    public JobType getSupportedType() {
        return JobType.SUMMARY;
    }

    @Override
    public String process(String extractedText) {
        log.info("SummaryStrategy - lancement du resume IA ({} chars)", extractedText.length());
        return aiProviderService.summarize(extractedText);
    }
}
