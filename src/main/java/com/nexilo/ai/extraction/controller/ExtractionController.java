package com.nexilo.ai.extraction.controller;
import com.nexilo.ai.extraction.dto.ExtractionRequest;
import com.nexilo.ai.extraction.dto.ExtractionResponse;
import com.nexilo.ai.extraction.dto.ExtractionTemplateResponse;
import com.nexilo.ai.extraction.entity.ExtractionResultEntity;
import com.nexilo.ai.extraction.repository.ExtractionResultRepository;
import com.nexilo.ai.extraction.service.ExportService;
import com.nexilo.ai.extraction.service.ExtractionService;
import com.nexilo.common.exception.ErrorCode;
import com.nexilo.common.exception.NexiloException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;
@Slf4j
@RestController
@RequiredArgsConstructor
@Tag(name = "Extraction", description = "Extraction de donnees structurees depuis les PDFs via IA")
public class ExtractionController {
    private final ExtractionService extractionService;
    private final ExportService exportService;
    private final ExtractionResultRepository resultRepository;

    @PostMapping("/api/v1/documents/{documentId}/extract")
    @Operation(summary = "Extraire des donnees structurees d'un document",
               description = "Fournissez soit un templateId (INVOICE, CONTRACT, CV_RESUME, MEDICAL) soit une liste de fields.")
    public ResponseEntity<ExtractionResponse> extract(
            @Parameter(description = "UUID du document source") @PathVariable UUID documentId,
            @Valid @RequestBody ExtractionRequest request) {
        log.info("Extraction demandee - docId={}, template={}", documentId, request.templateId());
        return ResponseEntity.status(HttpStatus.CREATED).body(extractionService.extract(documentId, request));
    }
    @GetMapping("/api/v1/documents/{documentId}/extractions")
    @Operation(summary = "Historique des extractions d'un document")
    public ResponseEntity<List<ExtractionResponse>> getExtractionHistory(@PathVariable UUID documentId) {
        return ResponseEntity.ok(extractionService.getExtractionHistory(documentId));
    }
    @GetMapping("/api/v1/extraction-templates")
    @Operation(summary = "Lister les templates d'extraction disponibles",
               description = "Retourne les 4 templates predefinis : INVOICE, CONTRACT, CV_RESUME, MEDICAL.")
    public ResponseEntity<List<ExtractionTemplateResponse>> listTemplates() {
        return ResponseEntity.ok(extractionService.listTemplates());
    }

    @GetMapping("/api/v1/extractions/{extractionId}/export")
    @Operation(summary = "Exporter un resultat d'extraction",
               description = "Formats supportes : json, csv, xlsx. Retourne un fichier telecharge.")
    public ResponseEntity<byte[]> export(
            @Parameter(description = "UUID du resultat d'extraction") @PathVariable UUID extractionId,
            @Parameter(description = "Format d'export : json | csv | xlsx", example = "xlsx")
            @RequestParam(defaultValue = "json") String format) {

        ExtractionResultEntity entity = resultRepository.findById(extractionId)
            .orElseThrow(() -> new NexiloException(ErrorCode.RESOURCE_NOT_FOUND,
                HttpStatus.NOT_FOUND, "Extraction introuvable: " + extractionId));

        byte[] data;
        MediaType mediaType;
        String extension;

        switch (format.toLowerCase()) {
            case "csv" -> {
                data = exportService.toCsv(entity);
                mediaType = MediaType.parseMediaType("text/csv;charset=UTF-8");
                extension = "csv";
            }
            case "xlsx" -> {
                data = exportService.toExcel(entity);
                mediaType = MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
                extension = "xlsx";
            }
            default -> {
                data = exportService.toJson(entity);
                mediaType = MediaType.APPLICATION_JSON;
                extension = "json";
            }
        }

        String filename = "extraction_" + extractionId + "." + extension;
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(mediaType);
        headers.setContentDisposition(ContentDisposition.attachment().filename(filename).build());
        headers.setContentLength(data.length);

        log.info("Export {} - extractionId={}, {} bytes", extension, extractionId, data.length);
        return ResponseEntity.ok().headers(headers).body(data);
    }
}