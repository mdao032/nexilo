package com.nexilo.core.conversion;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;
import java.time.Instant;
import java.util.UUID;
/**
 * Entite JPA representant un enregistrement de conversion de fichier.
 */
@Entity
@Table(name = "conversions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConversionRecord {
    @Id
    @UuidGenerator
    @Column(updatable = false, nullable = false)
    private UUID id;
    @Column(name = "user_id", nullable = false)
    private Long userId;
    @Column(name = "input_format", nullable = false, length = 10)
    private String inputFormat;
    @Column(name = "output_format", nullable = false, length = 10)
    private String outputFormat;
    @Column(nullable = false, length = 30)
    private String operation;
    @Column(name = "input_size_bytes")
    private Long inputSizeBytes;
    @Column(name = "output_size_bytes")
    private Long outputSizeBytes;
    @Column(nullable = false, length = 20)
    @Builder.Default
    private String status = "PENDING";
    @Column(name = "file_key", columnDefinition = "TEXT")
    private String fileKey;
    @Column(name = "job_id")
    private UUID jobId;
    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;
    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();
    @Column(name = "expires_at", insertable = false, updatable = false)
    private Instant expiresAt;
}