package com.nexilo.usage;

/**
 * Service de tracking d'usage IA.
 */
public interface UsageService {

    /**
     * Enregistre un usage de manière <em>asynchrone</em>.
     * Ne bloque jamais le thread appelant — les erreurs sont loguées mais ignorées.
     *
     * @param record usage à persister
     */
    void recordUsage(UsageRecord record);

    /**
     * Enregistre une conversion de fichier de manière <em>asynchrone</em>.
     * Raccourci pratique pour {@link #recordUsage} avec les champs conversion.
     *
     * @param userId          identifiant de l'utilisateur
     * @param inputFormat     format source ("PDF", "DOCX"…)
     * @param outputFormat    format résultat ("DOCX", "XLSX", "PNG"…)
     * @param inputSizeBytes  taille du fichier source en octets
     * @param outputSizeBytes taille du fichier résultat en octets (0 si inconnu)
     * @param durationMs      durée de la conversion en ms
     */
    void recordConversion(Long userId, String inputFormat, String outputFormat,
                          long inputSizeBytes, long outputSizeBytes, long durationMs);

    /**
     * Retourne un résumé agrégé d'usage pour un utilisateur sur une période.
     *
     * @param userId identifiant de l'utilisateur
     * @param period période (DAY, WEEK, MONTH, YEAR)
     * @return résumé détaillé par feature
     */
    UsageSummaryResponse getUsageSummary(Long userId, UsagePeriod period);
}

