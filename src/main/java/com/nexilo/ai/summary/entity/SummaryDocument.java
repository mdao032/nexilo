package com.nexilo.ai.summary.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Document uploadé pour analyse IA.
 * La clé d'idempotence est le hash SHA-256 du contenu binaire du PDF :
 * si le même fichier est uploadé plusieurs fois, un seul enregistrement est créé.
 */
@Entity
@Table(name = "summary_documents",
        uniqueConstraints = @UniqueConstraint(name = "uq_summary_doc_hash", columnNames = "content_hash"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SummaryDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    /** Nom original du fichier uploadé. */
    @Column(nullable = false)
    private String fileName;

    /** Taille du fichier en octets. */
    @Column(nullable = false)
    private Long fileSize;

    /**
     * Empreinte SHA-256 du contenu binaire du PDF.
     * Sert de clé d'idempotence : même PDF = même hash = résumé existant retourné.
     */
    @Column(name = "content_hash", nullable = false, length = 64)
    private String contentHash;

    /** ID de l'utilisateur propriétaire du document. */
    @Column(nullable = false)
    private Long userId;

    /** Date d'upload. */
    @Column(nullable = false, updatable = false)
    private Instant uploadedAt;

    /**
     * Texte extrait du PDF par PDFBox.
     * Stocké pour éviter de re-lire le PDF lors de l'ingestion RAG.
     */
    @Column(columnDefinition = "TEXT")
    private String extractedText;

    /** Clé de stockage du fichier PDF (chemin local ou clé MinIO). */
    @Column(name = "storage_key", length = 500)
    private String storageKey;

    /**
     * URL d'accès au fichier PDF.
     * En local : chemin relatif. En prod : URL pré-signée MinIO (1h).
     */
    @Column(name = "storage_url", columnDefinition = "TEXT")
    private String storageUrl;

    /**
     * Indique si le document a été ingéré dans le vector store (chunks + embeddings).
     * Initialisé à false à la création.
     */
    @Column(nullable = false)
    @Builder.Default
    private boolean ingested = false;

    /** Date/heure de l'ingestion dans le vector store. */
    private Instant ingestedAt;

    @PrePersist
    protected void onCreate() {
        this.uploadedAt = Instant.now();
    }
}

