package com.nexilo.ai.extraction.service;
import com.nexilo.ai.extraction.dto.ExtractionRequest;
import com.nexilo.ai.extraction.dto.ExtractionResponse;
import com.nexilo.ai.extraction.dto.ExtractionTemplateResponse;
import java.util.List;
import java.util.UUID;
public interface ExtractionService {
    ExtractionResponse extract(UUID documentId, ExtractionRequest request);
    List<ExtractionResponse> getExtractionHistory(UUID documentId);
    List<ExtractionTemplateResponse> listTemplates();
}