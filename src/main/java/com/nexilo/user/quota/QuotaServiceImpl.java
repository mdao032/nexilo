package com.nexilo.user.quota;

import com.nexilo.common.exception.ErrorCode;
import com.nexilo.common.exception.NexiloException;
import com.nexilo.user.entity.FeatureType;
import com.nexilo.user.entity.PlanConfig;
import com.nexilo.user.entity.User;
import com.nexilo.user.entity.UserPlan;
import com.nexilo.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;

/**
 * Implémentation Redis du service de quotas.
 *
 * <p>Clé Redis : {@code quota:{userId}:{feature}:{date}} (ex: {@code quota:42:SUMMARY:2026-04-26})
 * <br>Expiration calculée jusqu'à minuit UTC du jour courant.
 * <br>Incrémentation atomique via {@code INCR} — thread-safe sans verrou applicatif.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QuotaServiceImpl implements QuotaService {

    private final StringRedisTemplate redisTemplate;
    private final UserRepository userRepository;

    // =========================================================================
    // Public API
    // =========================================================================

    @Override
    public void checkAndIncrementQuota(Long userId, FeatureType feature) {
        User user = loadUser(userId);
        UserPlan plan = user.getPlan() != null ? user.getPlan() : UserPlan.FREE;
        PlanConfig config = PlanConfig.forPlan(plan);
        int limit = config.limitFor(feature);

        // -1 = illimité (ENTERPRISE ou feature système)
        if (limit < 0) {
            log.debug("Quota illimité pour userId={} feature={} plan={}", userId, feature, plan);
            return;
        }

        String key = buildKey(userId, feature);
        Long current;
        try {
            current = redisTemplate.opsForValue().increment(key);

            // Si c'est le premier incrément, poser l'expiration à minuit UTC
            if (current != null && current == 1L) {
                redisTemplate.expire(key, ttlUntilMidnight());
            }
        } catch (Exception e) {
            // Redis indisponible : on autorise l'opération avec un warning
            // (comportement permissif — quota non enforced sans Redis)
            log.warn("Redis indisponible — quota non vérifié pour userId={}, feature={} ({})",
                    userId, feature, e.getMessage());
            return;
        }

        if (current != null && current > limit) {
            // Décrémenter pour annuler l'incrément préventif
            try { redisTemplate.opsForValue().decrement(key); } catch (Exception ignored) {}
            log.warn("Quota dépassé — userId={}, feature={}, plan={}, limit={}, current={}",
                    userId, feature, plan, limit, current - 1);
            throw new PlanLimitExceededException(feature, limit, plan);
        }

        log.debug("Quota OK — userId={}, feature={}, {}/{}", userId, feature, current, limit);
    }

    @Override
    public QuotaStatusResponse getRemainingQuota(Long userId) {
        User user = loadUser(userId);
        UserPlan plan = user.getPlan() != null ? user.getPlan() : UserPlan.FREE;
        PlanConfig config = PlanConfig.forPlan(plan);

        long summariesUsed   = getCount(userId, FeatureType.SUMMARY);
        long qnaUsed         = getCount(userId, FeatureType.QNA);
        long extractionsUsed = getCount(userId, FeatureType.EXTRACTION);

        return new QuotaStatusResponse(
                plan,
                summariesUsed,   config.maxSummariesPerDay(),
                qnaUsed,         config.maxQnaPerDay(),
                extractionsUsed, config.maxExtractionsPerDay(),
                config.workflowsEnabled(),
                config.maxFileSizeMb()
        );
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    /** Construit la clé Redis du compteur journalier. */
    static String buildKey(Long userId, FeatureType feature) {
        return "quota:" + userId + ":" + feature.name() + ":" + LocalDate.now(ZoneOffset.UTC);
    }

    /** Calcule le TTL jusqu'à minuit UTC. */
    private Duration ttlUntilMidnight() {
        var now = java.time.LocalDateTime.now(ZoneOffset.UTC);
        var midnight = now.toLocalDate().plusDays(1).atStartOfDay();
        return Duration.between(now, midnight);
    }

    /** Lit le compteur depuis Redis (0 si absent ou Redis indisponible). */
    private long getCount(Long userId, FeatureType feature) {
        try {
            String value = redisTemplate.opsForValue().get(buildKey(userId, feature));
            if (value == null) return 0L;
            return Long.parseLong(value);
        } catch (Exception e) {
            log.warn("Redis indisponible — compteur quota retourné à 0 pour userId={}, feature={}", userId, feature);
            return 0L;
        }
    }

    private User loadUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new NexiloException(
                        ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND,
                        "Utilisateur introuvable : " + userId));
    }
}

