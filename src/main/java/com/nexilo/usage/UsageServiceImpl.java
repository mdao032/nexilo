package com.nexilo.usage;

import com.nexilo.user.entity.FeatureType;
import com.nexilo.user.entity.UserPlan;
import com.nexilo.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Implémentation du service de tracking d'usage.
 *
 * <p>{@link #recordUsage} est {@code @Async} : s'exécute dans le pool
 * {@code processingExecutor} sans bloquer le thread principal.
 * Toute erreur est loguée et avalée pour ne jamais impacter la réponse IA.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UsageServiceImpl implements UsageService {

    private final UsageRepository usageRepository;
    private final UserRepository userRepository;

    // =========================================================================
    // Enregistrement conversion (async)
    // =========================================================================

    @Override
    @Async("processingExecutor")
    public void recordConversion(Long userId, String inputFormat, String outputFormat,
                                 long inputSizeBytes, long outputSizeBytes, long durationMs) {
        try {
            UserPlan plan = userRepository.findById(userId)
                    .map(u -> u.getPlan() != null ? u.getPlan() : UserPlan.FREE)
                    .orElse(UserPlan.FREE);

            UsageRecord record = UsageRecord.builder()
                    .userId(userId)
                    .feature(FeatureType.CONVERSION)
                    .tokensUsed(0)          // pas de tokens pour les conversions
                    .costMicroUsd(0L)        // coût CPU — pas facturé en tokens
                    .inputFormat(inputFormat)
                    .outputFormat(outputFormat)
                    .inputSizeBytes(inputSizeBytes)
                    .outputSizeBytes(outputSizeBytes)
                    .responseTimeMs(durationMs)
                    .planAtTime(plan)
                    .createdAt(java.time.Instant.now())
                    .build();

            usageRepository.save(record);
            log.debug("Conversion enregistrée — userId={}, {}→{}, in={}B, out={}B, {}ms",
                    userId, inputFormat, outputFormat, inputSizeBytes, outputSizeBytes, durationMs);
        } catch (Exception e) {
            log.error("Erreur enregistrement conversion (ignorée) : {}", e.getMessage(), e);
        }
    }

    // =========================================================================
    // Enregistrement usage IA (async — ne bloque pas la réponse)
    // =========================================================================

    @Override
    @Async("processingExecutor")
    @Transactional
    public void recordUsage(UsageRecord record) {
        try {
            // Snapshotter le plan si non fourni
            if (record.getPlanAtTime() == null) {
                UserPlan plan = userRepository.findById(record.getUserId())
                        .map(u -> u.getPlan() != null ? u.getPlan() : UserPlan.FREE)
                        .orElse(UserPlan.FREE);
                record.setPlanAtTime(plan);
            }
            // Calculer le coût si non fourni
            if (record.getCostMicroUsd() == 0 && record.getTokensUsed() > 0) {
                record.setCostMicroUsd(UsageRecord.calculateCost(record.getTokensUsed()));
            }
            if (record.getCreatedAt() == null) {
                record.setCreatedAt(Instant.now());
            }
            usageRepository.save(record);
            log.debug("Usage enregistré — userId={}, feature={}, tokens={}, coût={}µ$",
                    record.getUserId(), record.getFeature(),
                    record.getTokensUsed(), record.getCostMicroUsd());
        } catch (Exception e) {
            // Ne jamais crasher le pipeline IA à cause du tracking
            log.error("Erreur lors de l'enregistrement d'usage (ignorée) : {}", e.getMessage(), e);
        }
    }

    // =========================================================================
    // Consultation
    // =========================================================================

    @Override
    @Transactional(readOnly = true)
    public UsageSummaryResponse getUsageSummary(Long userId, UsagePeriod period) {
        Instant now = Instant.now();
        Instant from = computeFrom(now, period);

        UserPlan plan = userRepository.findById(userId)
                .map(u -> u.getPlan() != null ? u.getPlan() : UserPlan.FREE)
                .orElse(UserPlan.FREE);

        List<Object[]> rows = usageRepository.aggregateByFeature(userId, from, now);

        List<UsageSummaryResponse.FeatureSummary> byFeature = rows.stream()
                .map(UsageSummaryResponse.FeatureSummary::from)
                .toList();

        long totalCalls  = byFeature.stream().mapToLong(UsageSummaryResponse.FeatureSummary::calls).sum();
        long totalTokens = byFeature.stream().mapToLong(UsageSummaryResponse.FeatureSummary::tokensUsed).sum();
        long totalCost   = byFeature.stream().mapToLong(UsageSummaryResponse.FeatureSummary::costMicroUsd).sum();

        return new UsageSummaryResponse(
                userId, plan, period.name(), from, now,
                totalCalls, totalTokens, totalCost, totalCost / 1_000_000.0,
                byFeature
        );
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private Instant computeFrom(Instant now, UsagePeriod period) {
        return switch (period) {
            case DAY   -> now.truncatedTo(ChronoUnit.DAYS);
            case WEEK  -> now.atZone(ZoneOffset.UTC).toLocalDate()
                             .with(java.time.DayOfWeek.MONDAY)
                             .atStartOfDay(ZoneOffset.UTC).toInstant();
            case MONTH -> now.atZone(ZoneOffset.UTC).toLocalDate()
                             .withDayOfMonth(1)
                             .atStartOfDay(ZoneOffset.UTC).toInstant();
            case YEAR  -> now.atZone(ZoneOffset.UTC).toLocalDate()
                             .withDayOfYear(1)
                             .atStartOfDay(ZoneOffset.UTC).toInstant();
        };
    }
}

