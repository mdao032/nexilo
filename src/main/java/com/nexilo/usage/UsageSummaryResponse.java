package com.nexilo.usage;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.nexilo.user.entity.FeatureType;
import com.nexilo.user.entity.UserPlan;

import java.time.Instant;
import java.util.List;

/**
 * Résumé d'usage IA pour un utilisateur sur une période.
 *
 * @param userId           identifiant de l'utilisateur
 * @param plan             plan actif
 * @param period           période couverte (DAY / MONTH)
 * @param from             début de la période
 * @param to               fin de la période
 * @param totalCalls       nombre total d'appels IA
 * @param totalTokensUsed  tokens totaux consommés
 * @param totalCostMicroUsd coût total en micro-dollars
 * @param totalCostUsd     coût total en dollars (pour affichage)
 * @param byFeature        détail par feature
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record UsageSummaryResponse(
        Long userId,
        UserPlan plan,
        String period,
        Instant from,
        Instant to,
        long totalCalls,
        long totalTokensUsed,
        long totalCostMicroUsd,
        double totalCostUsd,
        List<FeatureSummary> byFeature
) {

    /**
     * Détail d'usage pour une feature donnée.
     *
     * @param feature       type de fonctionnalité
     * @param calls         nombre d'appels
     * @param tokensUsed    tokens consommés
     * @param costMicroUsd  coût en micro-dollars
     * @param costUsd       coût en dollars
     */
    public record FeatureSummary(
            FeatureType feature,
            long calls,
            long tokensUsed,
            long costMicroUsd,
            double costUsd
    ) {
        public static FeatureSummary from(Object[] row) {
            FeatureType feature = (FeatureType) row[0];
            long calls          = ((Number) row[1]).longValue();
            long tokens         = row[2] != null ? ((Number) row[2]).longValue() : 0L;
            long cost           = row[3] != null ? ((Number) row[3]).longValue() : 0L;
            return new FeatureSummary(feature, calls, tokens, cost, cost / 1_000_000.0);
        }
    }
}

