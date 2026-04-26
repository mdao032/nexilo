package com.nexilo.ai.summary.repository;

import com.nexilo.ai.summary.entity.Summary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository pour les résumés IA générés.
 */
@Repository
public interface SummaryRepository extends JpaRepository<Summary, UUID> {

    /** Recherche le résumé associé à un document donné. */
    Optional<Summary> findByDocumentId(UUID documentId);
}

