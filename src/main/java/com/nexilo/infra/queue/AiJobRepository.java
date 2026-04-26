package com.nexilo.infra.queue;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository JPA pour les jobs IA asynchrones.
 */
@Repository
public interface AiJobRepository extends JpaRepository<AiJob, UUID> {

    /** Récupère tous les jobs d'un utilisateur, triés par date de création décroissante. */
    Page<AiJob> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    /** Récupère un job par son ID et son propriétaire (sécurité). */
    Optional<AiJob> findByIdAndUserId(UUID id, Long userId);
}

