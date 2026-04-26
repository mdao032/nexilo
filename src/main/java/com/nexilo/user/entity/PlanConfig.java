package com.nexilo.user.entity;

/**
 * Configuration des limites par plan.
 *
 * <p>Valeur {@code -1} = illimité (ENTERPRISE).
 *
 * @param maxSummariesPerDay       nombre max de résumés par jour
 * @param maxQnaPerDay             nombre max de questions Q&A par jour
 * @param maxExtractionsPerDay     nombre max d'extractions par jour
 * @param maxFileSizeMb            taille max de fichier IA en Mo
 * @param workflowsEnabled         workflows activés
 * @param maxConversionsPerDay     nombre max de conversions par jour
 * @param maxFileSizeMbConversion  taille max de fichier pour les conversions en Mo
 * @param libreOfficeEnabled       conversions Office→PDF via LibreOffice (PRO+)
 */
public record PlanConfig(
        int maxSummariesPerDay,
        int maxQnaPerDay,
        int maxExtractionsPerDay,
        int maxFileSizeMb,
        boolean workflowsEnabled,
        int maxConversionsPerDay,
        int maxFileSizeMbConversion,
        boolean libreOfficeEnabled
) {

    // -------------------------------------------------------------------------
    // Configurations par plan
    // -------------------------------------------------------------------------

    public static final PlanConfig FREE = new PlanConfig(
            5, 10, 2, 10, false,
            10, 10, false
    );

    public static final PlanConfig PRO = new PlanConfig(
            100, 500, 50, 100, true,
            200, 100, true
    );

    public static final PlanConfig ENTERPRISE = new PlanConfig(
            -1, -1, -1, 500, true,
            -1, 500, true
    );

    /** Retourne la config correspondant au plan donné. */
    public static PlanConfig forPlan(UserPlan plan) {
        return switch (plan) {
            case FREE       -> FREE;
            case PRO        -> PRO;
            case ENTERPRISE -> ENTERPRISE;
        };
    }

    /**
     * Retourne la limite journalière pour un type de fonctionnalité donné.
     * {@code -1} signifie illimité.
     */
    public int limitFor(FeatureType feature) {
        return switch (feature) {
            case SUMMARY    -> maxSummariesPerDay;
            case QNA        -> maxQnaPerDay;
            case EXTRACTION -> maxExtractionsPerDay;
            case CONVERSION -> maxConversionsPerDay;
            case INGEST     -> -1; // toujours illimité (opération système)
        };
    }
}
