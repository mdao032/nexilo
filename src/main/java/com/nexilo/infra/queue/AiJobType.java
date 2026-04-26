package com.nexilo.infra.queue;

/**
 * Type de job IA asynchrone.
 */
public enum AiJobType {
    /** Génération d'un résumé PDF. */
    SUMMARY,
    /** Ingestion d'un document dans le vector store (RAG). */
    INGEST,
    /** Question-réponse sur un document. */
    QNA,
    /** Extraction de données structurées. */
    EXTRACTION,
    /** Conversion de format (PDF↔Word, PDF↔Images, merge, split, compress…). */
    CONVERSION
}

