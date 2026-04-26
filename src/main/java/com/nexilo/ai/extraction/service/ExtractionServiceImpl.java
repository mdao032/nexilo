package com.nexilo.ai.extraction.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexilo.ai.extraction.dto.ExtractionField;
import com.nexilo.ai.extraction.dto.ExtractionRequest;
import com.nexilo.ai.extraction.dto.ExtractionResponse;
import com.nexilo.ai.extraction.dto.ExtractionTemplateResponse;
import com.nexilo.ai.extraction.entity.ExtractionResultEntity;
import com.nexilo.ai.extraction.entity.ExtractionTemplate;
import com.nexilo.ai.extraction.repository.ExtractionResultRepository;
import com.nexilo.ai.extraction.repository.ExtractionTemplateRepository;
import com.nexilo.ai.summary.entity.SummaryDocument;
import com.nexilo.ai.summary.repository.SummaryDocumentRepository;
import com.nexilo.common.exception.ErrorCode;
import com.nexilo.common.exception.NexiloException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import com.nexilo.user.entity.FeatureType;
import com.nexilo.user.quota.CheckQuota;
import com.nexilo.usage.UsageRecord;
import com.nexilo.usage.UsageService;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExtractionServiceImpl implements ExtractionService {

    private static final int MAX_TEXT_LENGTH = 80_000;

    @Value("${spring.ai.anthropic.chat.options.model:claude-sonnet-4-5}")
    private String modelName;

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;
    private final SummaryDocumentRepository documentRepository;
    private final ExtractionTemplateRepository templateRepository;
    private final ExtractionResultRepository resultRepository;
    private final UsageService usageService;

    @Override
    @Transactional
    @Cacheable(value = "extractions", key = "#documentId.toString() + ':' + (#request.templateId() != null ? #request.templateId() : 'custom')")
    @CheckQuota(feature = FeatureType.EXTRACTION)
    public ExtractionResponse extract(UUID documentId, ExtractionRequest request) {
        SummaryDocument document = documentRepository.findById(documentId)
            .orElseThrow(() -> new NexiloException(ErrorCode.DOCUMENT_NOT_FOUND, HttpStatus.NOT_FOUND, "Document introuvable: " + documentId));
        String text = resolveText(document);
        ExtractionTemplate template = null;
        List<ExtractionField> fields;
        if (request.templateId() != null && !request.templateId().isBlank()) {
            template = templateRepository.findByName(request.templateId().toUpperCase())
                .orElseThrow(() -> new NexiloException(ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND, "Template introuvable: " + request.templateId()));
            fields = parseTemplateFields(template.getFields());
        } else if (request.fields() != null && !request.fields().isEmpty()) {
            fields = request.fields();
        } else {
            throw new NexiloException(ErrorCode.VALIDATION_ERROR, HttpStatus.BAD_REQUEST, "Fournissez un templateId ou une liste de fields");
        }
        long t0 = System.currentTimeMillis();
        String rawJson = callClaudeForExtraction(text, fields);
        long responseTimeMs = System.currentTimeMillis() - t0;
        Map<String, Object> extracted = parseJson(rawJson);
        validateRequiredFields(fields, extracted);
        long filled = extracted.values().stream().filter(v -> v != null && !v.toString().isBlank()).count();
        double confidence = fields.isEmpty() ? 0.0 : (double) filled / fields.size();
        ExtractionResultEntity entity = resultRepository.save(ExtractionResultEntity.builder()
            .document(document).template(template)
            .fieldsUsed(writeJson(fields)).result(writeJson(extracted))
            .rawJson(rawJson).confidence(confidence).model(modelName)
            .tokensUsed((text.length() + rawJson.length()) / 4).build());
        log.info("Extraction OK - docId={}, champs={}/{}", documentId, filled, fields.size());

        // Tracking usage (async)
        int tokensEst = (text.length() + rawJson.length()) / 4;
        usageService.recordUsage(UsageRecord.builder()
                .userId(document.getUserId())
                .feature(FeatureType.EXTRACTION)
                .tokensUsed(tokensEst)
                .costMicroUsd(UsageRecord.calculateCost(tokensEst))
                .documentId(documentId)
                .responseTimeMs(responseTimeMs)
                .build());

        return toResponse(entity, extracted, false);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ExtractionResponse> getExtractionHistory(UUID documentId) {
        if (!documentRepository.existsById(documentId))
            throw new NexiloException(ErrorCode.DOCUMENT_NOT_FOUND, HttpStatus.NOT_FOUND, "Document introuvable: " + documentId);
        return resultRepository.findByDocumentIdOrderByCreatedAtDesc(documentId)
            .stream().map(e -> toResponse(e, parseJson(e.getResult()), false)).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ExtractionTemplateResponse> listTemplates() {
        return templateRepository.findAll().stream()
            .map(t -> new ExtractionTemplateResponse(t.getId(), t.getName(), t.getDescription(), parseTemplateFields(t.getFields())))
            .toList();
    }

    private String resolveText(SummaryDocument doc) {
        String text = doc.getExtractedText();
        if (text == null || text.isBlank())
            throw new NexiloException(ErrorCode.PROCESSING_ERROR, HttpStatus.UNPROCESSABLE_ENTITY,
                "Document non traite. Lancez d'abord POST /summarize");
        return text.length() > MAX_TEXT_LENGTH ? text.substring(0, MAX_TEXT_LENGTH) : text;
    }

    @Retryable(retryFor = NexiloException.class, maxAttempts = 3,
               backoff = @Backoff(delay = 2000, multiplier = 2.0, maxDelay = 30_000))
    public String callClaudeForExtraction(String text, List<ExtractionField> fields) {
        log.info("Appel Claude extraction - {} champs, {} chars", fields.size(), text.length());
        String fieldsDesc = buildFieldsDescription(fields);
        try {
            String result = chatClient.prompt()
                .system("You are a precise data extraction engine. Respond ONLY with a valid JSON object. No markdown, no explanation. If a field is not found use null. LIST fields -> JSON array. NUMBER fields -> numeric value. DATE fields -> ISO 8601. BOOLEAN fields -> true or false.")
                .user(u -> u.text("Extract the following fields from the document.\nRespond ONLY with a valid JSON object.\n\nFields:\n{fields}\n\nDocument:\n{document}")
                    .param("fields", fieldsDesc).param("document", text))
                .call().content();
            if (result == null || result.isBlank())
                throw new NexiloException(ErrorCode.AI_SERVICE_ERROR, HttpStatus.SERVICE_UNAVAILABLE, "Claude a retourne une reponse vide");
            return cleanJsonResponse(result.trim());
        } catch (NexiloException e) { throw e;
        } catch (Exception e) {
            log.error("Erreur Claude extraction: {}", e.getMessage());
            throw new NexiloException(ErrorCode.AI_SERVICE_ERROR, HttpStatus.SERVICE_UNAVAILABLE, "Service IA indisponible: " + e.getMessage(), e);
        }
    }

    private String buildFieldsDescription(List<ExtractionField> fields) {
        StringBuilder sb = new StringBuilder();
        for (ExtractionField f : fields)
            sb.append(String.format("- %s (%s%s): %s\n", f.name(),
                f.type() != null ? f.type() : "STRING", f.required() ? ", REQUIRED" : "", f.description()));
        return sb.toString();
    }

    private String cleanJsonResponse(String raw) {
        if (raw.startsWith("`")) { int s = raw.indexOf('\n'), e = raw.lastIndexOf("`");
            if (s != -1 && e > s) return raw.substring(s + 1, e).trim(); }
        int start = raw.indexOf('{');
        return start > 0 ? raw.substring(start) : raw;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseJson(String json) {
        try { return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {}); }
        catch (Exception e) {
            throw new NexiloException(ErrorCode.AI_SERVICE_ERROR, HttpStatus.UNPROCESSABLE_ENTITY,
                "Reponse IA non parseable: " + e.getMessage()); }
    }

    private String writeJson(Object obj) {
        try { return objectMapper.writeValueAsString(obj); }
        catch (Exception e) { throw new NexiloException(ErrorCode.PROCESSING_ERROR, HttpStatus.INTERNAL_SERVER_ERROR, "Serialisation JSON", e); }
    }

    private List<ExtractionField> parseTemplateFields(String json) {
        try { JsonNode arr = objectMapper.readTree(json); List<ExtractionField> r = new ArrayList<>();
            for (JsonNode n : arr) r.add(new ExtractionField(n.path("name").asText(), n.path("description").asText(),
                n.path("type").asText("STRING"), n.path("required").asBoolean(false)));
            return r;
        } catch (Exception e) { throw new NexiloException(ErrorCode.PROCESSING_ERROR, HttpStatus.INTERNAL_SERVER_ERROR, "Lecture template", e); }
    }

    private void validateRequiredFields(List<ExtractionField> fields, Map<String, Object> extracted) {
        fields.stream().filter(ExtractionField::required)
            .filter(f -> { Object v = extracted.get(f.name()); return v == null || v.toString().isBlank() || v.toString().equals("null"); })
            .map(ExtractionField::name).forEach(n -> log.warn("Champ requis manquant: {}", n));
    }

    private ExtractionResponse toResponse(ExtractionResultEntity e, Map<String, Object> fields, boolean cached) {
        return new ExtractionResponse(e.getId(), e.getDocument().getId(),
            e.getTemplate() != null ? e.getTemplate().getName() : "CUSTOM",
            fields, e.getConfidence(), e.getRawJson(), e.getModel(), e.getTokensUsed(), e.getCreatedAt(), cached);
    }
}
