package com.nexilo.ai.summary.repository;

import com.nexilo.ai.summary.entity.SummaryDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository pour les documents uploadés en vue d'un résumé IA.
 */
@Repository
public interface SummaryDocumentRepository extends JpaRepository<SummaryDocument, UUID> {

    /**
     * Recherche un document par son empreinte SHA-256.
     * Utilisé pour l'idempotence : même PDF → résumé existant.
     */
    Optional<SummaryDocument> findByContentHash(String contentHash);
}

