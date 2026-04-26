package com.nexilo.ai.extraction.dto;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
public record ExtractionResponse(
    UUID id, UUID documentId, String templateName,
    Map<String, Object> fields, Double confidence, String rawJson,
    String model, Integer tokensUsed, LocalDateTime createdAt, boolean cached
) {}