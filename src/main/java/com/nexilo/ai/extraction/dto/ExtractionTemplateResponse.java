package com.nexilo.ai.extraction.dto;
import java.util.List;
import java.util.UUID;
public record ExtractionTemplateResponse(UUID id, String name, String description, List<ExtractionField> fields) {}