package com.nexilo.processing.dto;

import com.nexilo.processing.entity.ProcessingJob.JobType;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * DTO encapsulant la requete de traitement d'un fichier.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProcessingRequest {

    /** Fichier a traiter. Obligatoire. */
    @NotNull(message = "File is required")
    private MultipartFile file;

    /** Type de traitement souhaite. SUMMARY par defaut. */
    @Builder.Default
    private JobType jobType = JobType.SUMMARY;

    /** Nom optionnel pour identifier le job. */
    private String name;
}
