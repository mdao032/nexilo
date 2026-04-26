package com.nexilo.usage;

import com.nexilo.user.entity.FeatureType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Repository JPA pour les enregistrements d'usage.
 */
@Repository
public interface UsageRepository extends JpaRepository<UsageRecord, UUID> {

    /**
     * Compte les appels par feature sur une période.
     */
    @Query("SELECT COUNT(u) FROM UsageRecord u WHERE u.userId = :userId AND u.feature = :feature AND u.createdAt >= :from")
    long countByUserIdAndFeatureAndCreatedAtAfter(
            @Param("userId") Long userId,
            @Param("feature") FeatureType feature,
            @Param("from") Instant from);

    /**
     * Agrégat d'usage par feature pour un utilisateur sur une période.
     * Retourne : [feature, count, totalTokens, totalCostMicroUsd]
     */
    @Query("""
            SELECT u.feature, COUNT(u), SUM(u.tokensUsed), SUM(u.costMicroUsd)
            FROM UsageRecord u
            WHERE u.userId = :userId AND u.createdAt >= :from AND u.createdAt < :to
            GROUP BY u.feature
            ORDER BY u.feature
            """)
    List<Object[]> aggregateByFeature(
            @Param("userId") Long userId,
            @Param("from") Instant from,
            @Param("to") Instant to);

    /**
     * Historique paginé (non paginé ici, limité à 500 par requête).
     */
    List<UsageRecord> findByUserIdAndCreatedAtBetweenOrderByCreatedAtDesc(
            Long userId, Instant from, Instant to);
}

