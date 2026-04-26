package com.nexilo.ai.extraction.dto;
import java.util.List;
public record ExtractionRequest(String templateId, List<ExtractionField> fields) {}