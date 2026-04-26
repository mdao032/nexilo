package com.nexilo.user.quota;

import com.nexilo.user.entity.FeatureType;

import java.lang.annotation.*;

/**
 * Déclenche la vérification du quota avant l'exécution de la méthode annotée.
 *
 * <p>Utilisé par {@link QuotaCheckAspect} pour intercepter les appels IA coûteux
 * et vérifier que l'utilisateur n'a pas dépassé les limites de son plan.
 *
 * <p>Exemple :
 * <pre>
 * {@literal @}CheckQuota(feature = FeatureType.SUMMARY)
 * public SummaryResponse summarize(MultipartFile file, Long userId) { ... }
 * </pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface CheckQuota {

    /** Type de fonctionnalité à vérifier. */
    FeatureType feature();
}

