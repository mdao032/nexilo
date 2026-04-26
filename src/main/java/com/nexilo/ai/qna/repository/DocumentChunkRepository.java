package com.nexilo.ai.qna.repository;

import com.nexilo.ai.qna.entity.DocumentChunk;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Repository pour les chunks de documents vectorisés.
 */
@Repository
public interface DocumentChunkRepository extends JpaRepository<DocumentChunk, UUID> {

    /** Récupère tous les chunks d'un document, triés par position. */
    List<DocumentChunk> findByDocumentIdOrderByChunkIndex(UUID documentId);

    /** Compte le nombre de chunks d'un document. */
    long countByDocumentId(UUID documentId);

    /** Supprime tous les chunks d'un document (avant ré-ingestion). */
    @Modifying
    @Query("DELETE FROM DocumentChunk c WHERE c.document.id = :documentId")
    void deleteByDocumentId(@Param("documentId") UUID documentId);
}

