package com.nexilo.processing.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.web.multipart.MultipartFile;

/**
 * Data Transfer Object (DTO) that encapsulates the payload for processing physical files.
 * This class captures the file alongside metadata dictating how the file should be handled.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProcessingRequest {

    /**
     * The physical file to process.
     * Must not be null.
     */
    @NotNull(message = "File is required")
    private MultipartFile file;

    /**
     * The desired type of extraction/processing (e.g., "SUMMARY", "INSIGHTS").
     * Optional field.
     */
    private String type;

    /**
     * An optional custom name serving as an alias or title override for the file.
     */
    private String name;
}
