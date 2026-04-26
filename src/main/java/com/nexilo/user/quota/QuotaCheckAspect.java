package com.nexilo.user.quota;

import com.nexilo.common.exception.ErrorCode;
import com.nexilo.common.exception.NexiloException;
import com.nexilo.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Aspect AOP qui intercept toute méthode annotée {@link CheckQuota} et vérifie
 * le quota de l'utilisateur courant <em>avant</em> l'exécution de la méthode.
 *
 * <p>L'identifiant de l'utilisateur est résolu depuis le {@code SecurityContext} :
 * on utilise le hashCode de l'email (même logique que les controllers) jusqu'à
 * ce qu'un vrai {@code UserDetails} enrichi soit en place.
 *
 * <p>spring-aspects est déjà déclaré dans pom.xml — aucune dépendance à ajouter.
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class QuotaCheckAspect {

    private final QuotaService quotaService;
    private final UserRepository userRepository;

    /**
     * Intercepte toute méthode annotée {@code @CheckQuota} et vérifie le quota.
     *
     * @param joinPoint   point de jonction AOP
     * @param checkQuota  annotation portant le type de feature
     */
    @Before("@annotation(checkQuota)")
    public void beforeQuotaCheck(JoinPoint joinPoint, CheckQuota checkQuota) {
        Long userId = resolveCurrentUserId();
        log.debug("Vérification quota — feature={}, userId={}, méthode={}",
                checkQuota.feature(), userId, joinPoint.getSignature().getName());
        // Lève PlanLimitExceededException si la limite est atteinte
        quotaService.checkAndIncrementQuota(userId, checkQuota.feature());
    }

    // =========================================================================
    // Helper
    // =========================================================================

    /**
     * Résout l'ID réel de l'utilisateur depuis le SecurityContext.
     * Le subject JWT est l'email — on cherche l'ID en base via UserRepository.
     */
    private Long resolveCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            throw new NexiloException(ErrorCode.INVALID_CREDENTIALS, HttpStatus.UNAUTHORIZED,
                    "Utilisateur non authentifié");
        }
        String email = auth.getName();
        return userRepository.findByEmail(email)
                .map(u -> u.getId())
                .orElseThrow(() -> new NexiloException(ErrorCode.INVALID_CREDENTIALS,
                        HttpStatus.UNAUTHORIZED, "Utilisateur introuvable : " + email));
    }
}

