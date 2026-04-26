package com.nexilo.ai.summary.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Résumé IA généré pour un document.
 * Relation 1-to-1 avec {@link SummaryDocument}.
 */
@Entity
@Table(name = "summaries")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Summary {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    /** Document source. */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id", nullable = false, unique = true)
    private SummaryDocument document;

    /** Contenu du résumé généré par Claude. */
    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    /** Langue détectée du document (ex: "fr", "en"). */
    @Column(length = 10)
    private String language;

    /** Nom du modèle IA utilisé (ex: "claude-sonnet-4-5"). */
    @Column(length = 100)
    private String model;

    /** Nombre de tokens estimés utilisés. */
    private Integer tokensUsed;

    /** Date de création du résumé. */
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
    }
}

