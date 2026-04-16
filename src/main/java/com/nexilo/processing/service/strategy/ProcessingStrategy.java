package com.nexilo.processing.service.strategy;

import com.nexilo.processing.entity.ProcessingJob.JobType;

/**
 * Interface Strategy pour le traitement de documents.
 * Chaque type de traitement (SUMMARY, QA, EXTRACTION, etc.) implemente cette interface.
 */
public interface ProcessingStrategy {
    /** Retourne le type de job supporte par cette strategie. */
    JobType getSupportedType();
    /** Execute le traitement et retourne le resultat sous forme de texte. */
    String process(String extractedText);
}
