package com.nexilo.user.quota;

import com.nexilo.user.entity.FeatureType;
import com.nexilo.user.entity.UserPlan;

/**
 * Exception levée quand un utilisateur dépasse la limite de son plan.
 * Mappée en HTTP 429 par {@link com.nexilo.common.exception.GlobalExceptionHandler}.
 */
public class PlanLimitExceededException extends RuntimeException {

    private final FeatureType feature;
    private final int limit;
    private final UserPlan plan;

    public PlanLimitExceededException(FeatureType feature, int limit, UserPlan plan) {
        super(String.format(
                "Limite journalière atteinte pour %s (plan %s : %d/jour). Passez au plan supérieur.",
                feature, plan, limit));
        this.feature = feature;
        this.limit = limit;
        this.plan = plan;
    }

    public FeatureType getFeature() { return feature; }
    public int getLimit() { return limit; }
    public UserPlan getPlan() { return plan; }
}

