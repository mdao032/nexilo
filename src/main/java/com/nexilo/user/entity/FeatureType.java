package com.nexilo.user.entity;

/**
 * Type de fonctionnalité soumis au quota.
 */
public enum FeatureType {
    SUMMARY,
    QNA,
    EXTRACTION,
    INGEST,
    /** Conversion de fichiers (PDF↔Word, merge, split, compress…). */
    CONVERSION
}

