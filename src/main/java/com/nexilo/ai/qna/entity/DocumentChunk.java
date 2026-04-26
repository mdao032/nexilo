package com.nexilo.ai.qna.entity;

import com.nexilo.ai.summary.entity.SummaryDocument;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * Chunk de texte extrait d'un document, prêt pour l'indexation sémantique.
 *
 * <p><b>Note :</b> La colonne {@code embedding} (type {@code vector(1536)}) n'est PAS
 * mappée ici — Hibernate ne connaît pas le type pgvector.
 * L'embedding est inséré/mis à jour via {@code JdbcTemplate} dans
 * {@link com.nexilo.ai.qna.service.DocumentIngestionService}.
 */
@Entity
@Table(name = "document_chunks")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DocumentChunk {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    /** Document source du chunk. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id", nullable = false)
    private SummaryDocument document;

    /** Contenu textuel du chunk. */
    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    /** Position du chunk dans le document (0-based). */
    @Column(name = "chunk_index", nullable = false)
    private int chunkIndex;

    /**
     * Métadonnées JSON libres (page, titre de section, etc.).
     * Stocké en JSONB PostgreSQL.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String metadata;

    /** Date de création. */
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
    }
}

