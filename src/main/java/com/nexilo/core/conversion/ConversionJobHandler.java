package com.nexilo.core.conversion;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexilo.infra.queue.AiJob;
import com.nexilo.infra.queue.AiJobRepository;
import com.nexilo.storage.FileStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Gestionnaire de jobs de conversion asynchrones.
 *
 * <p>Traite les jobs de type {@code CONVERSION} soumis par {@link ConversionController}
 * pour les fichiers volumineux (> 10 MB). La progression passe de
 * {@code PENDING → PROCESSING → DONE/FAILED}.
 *
 * <p>Flux :
 * <ol>
 *   <li>Le contrôleur stocke le fichier source dans MinIO/local sous une clé temporaire.</li>
 *   <li>Le contrôleur soumet un job avec le payload JSON : {@code {operation, inputKey, outputExt}}.</li>
 *   <li>Ce handler récupère le fichier, applique la conversion, stocke le résultat.</li>
 *   <li>La clé du fichier résultat est sauvegardée dans {@link ConversionRecord} et dans le job.</li>
 * </ol>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ConversionJobHandler {

    private final ConversionService conversionService;
    private final PdfManipulationService pdfManipulationService;
    private final FileStorageService fileStorageService;
    private final ConversionRepository conversionRepository;
    private final AiJobRepository jobRepository;
    private final ObjectMapper objectMapper;

    /**
     * Point d'entrée appelé par {@link com.nexilo.infra.queue.AiJobProcessor}
     * pour les jobs de type CONVERSION.
     *
     * @param job le job à traiter
     * @return JSON contenant la clé du fichier résultat et sa taille
     */
    public String handle(AiJob job) throws Exception {
        log.info("ConversionJobHandler — traitement job {} (user={})", job.getId(), job.getUserId());

        // Désérialiser le payload
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = objectMapper.readValue(job.getPayload(), Map.class);

        String operation = (String) payload.get("operation");
        String inputKey  = (String) payload.get("inputKey");
        String recordId  = (String) payload.get("recordId");

        if (operation == null || inputKey == null) {
            throw new IllegalArgumentException("Payload incomplet : operation et inputKey sont requis");
        }

        // Mettre à jour le ConversionRecord en PROCESSING
        ConversionRecord record = resolveRecord(recordId, job);
        record.setStatus("PROCESSING");
        conversionRepository.save(record);

        // Récupérer le fichier source depuis le stockage
        byte[] inputBytes;
        try (InputStream is = fileStorageService.retrieve(inputKey)) {
            inputBytes = is.readAllBytes();
        }

        // Appliquer la conversion
        byte[] outputBytes = applyConversion(operation, inputBytes, payload);

        // 3. Stocker le résultat
        String outputExt = getOutputExtension(operation);
        String outputKey = "conversions/" + job.getUserId() + "/" + job.getId() + "/result." + outputExt;
        fileStorageService.store(toMultipartFile(outputBytes, "result." + outputExt), outputKey);

        // Mettre à jour le ConversionRecord en DONE
        record.setStatus("DONE");
        record.setFileKey(outputKey);
        record.setOutputSizeBytes((long) outputBytes.length);
        conversionRepository.save(record);

        // Supprimer le fichier source temporaire
        try {
            fileStorageService.delete(inputKey);
        } catch (Exception e) {
            log.warn("Impossible de supprimer le fichier source temporaire {} : {}", inputKey, e.getMessage());
        }

        log.info("ConversionJobHandler — job {} terminé, résultat={} ({} bytes)",
                job.getId(), outputKey, outputBytes.length);

        return objectMapper.writeValueAsString(Map.of(
                "outputKey", outputKey,
                "outputSizeBytes", outputBytes.length,
                "operation", operation
        ));
    }

    // =========================================================================
    // Dispatch par opération
    // =========================================================================

    private byte[] applyConversion(String operation, byte[] input, Map<String, Object> payload) throws Exception {
        return switch (operation) {
            case "TO_WORD"      -> conversionService.pdfToWord(input);
            case "TO_EXCEL"     -> conversionService.pdfToExcel(input);
            case "WORD_TO_PDF"  -> conversionService.officeToPdf(input, "docx");
            case "EXCEL_TO_PDF" -> conversionService.officeToPdf(input, "xlsx");
            case "PPT_TO_PDF"   -> conversionService.officeToPdf(input, "pptx");
            case "MERGE"        -> handleMerge(payload);
            case "COMPRESS" -> {
                String lvl = (String) payload.getOrDefault("level", "EBOOK");
                yield pdfManipulationService.compress(input, CompressionLevel.valueOf(lvl));
            }
            case "TO_IMAGES" -> {
                // Images multiples → ZIP (premier byte[] de la liste)
                String fmt = (String) payload.getOrDefault("format", "png");
                int dpi = ((Number) payload.getOrDefault("dpi", 150)).intValue();
                List<byte[]> imgs = conversionService.pdfToImages(input, fmt, dpi);
                yield zipBytes(imgs, fmt);
            }
            default -> throw new IllegalArgumentException("Opération inconnue : " + operation);
        };
    }

    private byte[] handleMerge(Map<String, Object> payload) throws Exception {
        @SuppressWarnings("unchecked")
        List<String> keys = (List<String>) payload.get("inputKeys");
        if (keys == null || keys.isEmpty()) throw new IllegalArgumentException("inputKeys requis pour MERGE");
        java.util.List<byte[]> pdfs = new java.util.ArrayList<>();
        for (String k : keys) {
            try (InputStream is = fileStorageService.retrieve(k)) {
                pdfs.add(is.readAllBytes());
            }
        }
        return pdfManipulationService.merge(pdfs);
    }

    private byte[] zipBytes(List<byte[]> items, String ext) throws Exception {
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        try (java.util.zip.ZipOutputStream zos = new java.util.zip.ZipOutputStream(baos)) {
            for (int i = 0; i < items.size(); i++) {
                zos.putNextEntry(new java.util.zip.ZipEntry("page_" + (i + 1) + "." + ext));
                zos.write(items.get(i));
                zos.closeEntry();
            }
        }
        return baos.toByteArray();
    }

    private String getOutputExtension(String operation) {
        return switch (operation) {
            case "TO_WORD"                  -> "docx";
            case "TO_EXCEL"                 -> "xlsx";
            case "TO_IMAGES"                -> "zip";
            case "WORD_TO_PDF", "EXCEL_TO_PDF", "PPT_TO_PDF", "MERGE", "COMPRESS" -> "pdf";
            default                         -> "bin";
        };
    }

    private ConversionRecord resolveRecord(String recordId, AiJob job) {
        if (recordId != null) {
            return conversionRepository.findById(UUID.fromString(recordId))
                    .orElseGet(() -> buildRecord(job));
        }
        return buildRecord(job);
    }

    private ConversionRecord buildRecord(AiJob job) {
        return ConversionRecord.builder()
                .userId(job.getUserId())
                .inputFormat("?")
                .outputFormat("?")
                .operation("ASYNC")
                .jobId(job.getId())
                .build();
    }

    /** Adapte un tableau de bytes en MultipartFile minimal pour FileStorageService. */
    private MultipartFile toMultipartFile(byte[] bytes, String filename) {
        return new MultipartFile() {
            @Override public String getName() { return "file"; }
            @Override public String getOriginalFilename() { return filename; }
            @Override public String getContentType() { return "application/octet-stream"; }
            @Override public boolean isEmpty() { return bytes.length == 0; }
            @Override public long getSize() { return bytes.length; }
            @Override public byte[] getBytes() { return bytes; }
            @Override public InputStream getInputStream() { return new ByteArrayInputStream(bytes); }
            @Override public void transferTo(File dest) throws IOException {
                try (FileOutputStream fos = new FileOutputStream(dest)) { fos.write(bytes); }
            }
        };
    }
}

