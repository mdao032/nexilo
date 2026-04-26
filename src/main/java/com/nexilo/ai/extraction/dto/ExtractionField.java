package com.nexilo.ai.extraction.dto;
import jakarta.validation.constraints.NotBlank;
public record ExtractionField(
    @NotBlank String name,
    @NotBlank String description,
    String type,
    boolean required
) {
    public ExtractionField {
        if (type == null || type.isBlank()) type = "STRING";
    }
}