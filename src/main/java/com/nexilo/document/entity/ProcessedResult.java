package com.nexilo.document.entity;

import com.nexilo.processing.entity.ProcessingJob;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProcessedResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne
    private Document document;

    @OneToOne
    private ProcessingJob job;

    @Column(columnDefinition = "TEXT")
    private String summary;

    @Column(columnDefinition = "TEXT")
    private String insights;

    private LocalDateTime createdAt;

    /**
     * Initialise la date de création juste avant la persistance de l'entité en base de données.
     */
    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}
