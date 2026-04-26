package com.nexilo.ai.extraction.entity;

import com.nexilo.ai.summary.entity.SummaryDocument;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "extraction_results")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class ExtractionResultEntity {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id", nullable = false)
    private SummaryDocument document;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "template_id")
    private ExtractionTemplate template;
    @Column(nullable = false, columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String fieldsUsed;
    @Column(nullable = false, columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String result;
    @Column(columnDefinition = "TEXT")
    private String rawJson;
    private Double confidence;
    @Column(length = 100)
    private String model;
    private Integer tokensUsed;
    @Column(nullable = false)
    private LocalDateTime createdAt;
    @PrePersist
    protected void onCreate() { if (createdAt == null) createdAt = LocalDateTime.now(); }
}
