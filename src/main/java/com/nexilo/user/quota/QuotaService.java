package com.nexilo.user.quota;

import com.nexilo.user.entity.FeatureType;

/**
 * Service de vérification et d'incrémentation des quotas journaliers.
 */
public interface QuotaService {

    /**
     * Vérifie si l'utilisateur peut consommer une unité de la fonctionnalité donnée,
     * puis incrémente le compteur de manière atomique.
     *
     * @param userId  identifiant de l'utilisateur
     * @param feature type de fonctionnalité
     * @throws PlanLimitExceededException si la limite journalière est atteinte
     */
    void checkAndIncrementQuota(Long userId, FeatureType feature);

    /**
     * Retourne l'état des quotas pour l'utilisateur courant.
     *
     * @param userId identifiant de l'utilisateur
     * @return quotas restants par feature
     */
    QuotaStatusResponse getRemainingQuota(Long userId);
}

