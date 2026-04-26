package com.nexilo.user.quota;

import com.nexilo.user.entity.UserPlan;

/**
 * Réponse affichant les quotas restants pour l'utilisateur courant.
 *
 * @param plan                   plan actif
 * @param summariesUsed          résumés utilisés aujourd'hui
 * @param summariesLimit         limite journalière (-1 = illimité)
 * @param qnaUsed                questions Q&A utilisées aujourd'hui
 * @param qnaLimit               limite journalière
 * @param extractionsUsed        extractions utilisées aujourd'hui
 * @param extractionsLimit       limite journalière
 * @param workflowsEnabled       workflows activés sur ce plan
 * @param maxFileSizeMb          taille max de fichier en Mo
 */
public record QuotaStatusResponse(
        UserPlan plan,
        long summariesUsed,
        int summariesLimit,
        long qnaUsed,
        int qnaLimit,
        long extractionsUsed,
        int extractionsLimit,
        boolean workflowsEnabled,
        int maxFileSizeMb
) {}

