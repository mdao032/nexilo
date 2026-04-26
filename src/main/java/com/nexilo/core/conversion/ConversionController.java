package com.nexilo.core.conversion;

import com.nexilo.common.exception.ErrorCode;
import com.nexilo.common.exception.NexiloException;
import com.nexilo.infra.queue.AiJobService;
import com.nexilo.infra.queue.AiJobType;
import com.nexilo.storage.FileStorageService;
import com.nexilo.user.entity.FeatureType;
import com.nexilo.user.quota.CheckQuota;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import com.nexilo.user.repository.UserRepository;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * API de conversion et manipulation de fichiers PDF.
 *
 * <p>Tous les endpoints :
 * <ul>
 *   <li>Acceptent {@code multipart/form-data}</li>
 *   <li>Retournent le fichier converti en {@code ResponseEntity<byte[]>}</li>
 *   <li>Fichiers > 10 MB → délégation en job asynchrone (AiJobService)</li>
 * </ul>
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@Tag(name = "Conversion", description = "Conversion et manipulation de fichiers PDF")
public class ConversionController {

    /** Seuil (bytes) au-delà duquel le traitement est délégué en job async. */
    private static final long ASYNC_THRESHOLD_BYTES = 10L * 1024 * 1024; // 10 MB

    private final ConversionService conversionService;
    private final PdfManipulationService pdfManipulationService;
    private final AiJobService aiJobService;
    private final ConversionRepository conversionRepository;
    private final FileStorageService fileStorageService;
    private final UserRepository userRepository;

    // =========================================================================
    // Conversions PDF → autres formats
    // =========================================================================

    @PostMapping("/api/v1/convert/pdf-to-word")
    @Operation(summary = "PDF → Word (.docx)")
    @CheckQuota(feature = FeatureType.CONVERSION)
    public ResponseEntity<?> pdfToWord(
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal UserDetails userDetails) throws Exception {

        if (file.getSize() > ASYNC_THRESHOLD_BYTES) {
            return submitAsync(file, "TO_WORD", userDetails);
        }
        byte[] result = conversionService.pdfToWord(file.getBytes());
        return fileResponse(result, "converted.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
    }

    @PostMapping("/api/v1/convert/pdf-to-images")
    @Operation(summary = "PDF → Images (PNG ou JPG, DPI configurable)")
    @CheckQuota(feature = FeatureType.CONVERSION)
    public ResponseEntity<?> pdfToImages(
            @RequestParam("file") MultipartFile file,
            @RequestParam(defaultValue = "png") String format,
            @RequestParam(defaultValue = "150") int dpi,
            @AuthenticationPrincipal UserDetails userDetails) throws Exception {

        if (file.getSize() > ASYNC_THRESHOLD_BYTES) {
            return submitAsync(file, "TO_IMAGES", userDetails);
        }
        List<byte[]> images = conversionService.pdfToImages(file.getBytes(), format, dpi);
        // Si une seule page : retourner l'image directement ; sinon ZIP
        if (images.size() == 1) {
            String mime = "jpg".equalsIgnoreCase(format) ? "image/jpeg" : "image/png";
            return fileResponse(images.get(0), "page_1." + format, mime);
        }
        // Multiple pages → ZIP
        byte[] zip = zipImages(images, format);
        return fileResponse(zip, "pages.zip", "application/zip");
    }

    @PostMapping("/api/v1/convert/pdf-to-excel")
    @Operation(summary = "PDF → Excel (.xlsx) — extraction tabulaire")
    @CheckQuota(feature = FeatureType.CONVERSION)
    public ResponseEntity<?> pdfToExcel(
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal UserDetails userDetails) throws Exception {

        if (file.getSize() > ASYNC_THRESHOLD_BYTES) {
            return submitAsync(file, "TO_EXCEL", userDetails);
        }
        byte[] result = conversionService.pdfToExcel(file.getBytes());
        return fileResponse(result, "extracted.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    }

    @PostMapping("/api/v1/convert/images-to-pdf")
    @Operation(summary = "Images → PDF (JPG, PNG, WEBP)")
    @CheckQuota(feature = FeatureType.CONVERSION)
    public ResponseEntity<?> imagesToPdf(
            @RequestParam("files") List<MultipartFile> files,
            @AuthenticationPrincipal UserDetails userDetails) {

        byte[] result = conversionService.imagesToPdf(files);
        return fileResponse(result, "assembled.pdf", "application/pdf");
    }

    @PostMapping("/api/v1/convert/word-to-pdf")
    @Operation(summary = "Word → PDF via LibreOffice headless")
    @CheckQuota(feature = FeatureType.CONVERSION)
    public ResponseEntity<?> wordToPdf(
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal UserDetails userDetails) throws Exception {

        if (file.getSize() > ASYNC_THRESHOLD_BYTES) {
            return submitAsync(file, "WORD_TO_PDF", userDetails);
        }
        byte[] result = conversionService.officeToPdf(file.getBytes(), "docx");
        return fileResponse(result, "converted.pdf", "application/pdf");
    }

    @PostMapping("/api/v1/convert/excel-to-pdf")
    @Operation(summary = "Excel → PDF via LibreOffice headless")
    @CheckQuota(feature = FeatureType.CONVERSION)
    public ResponseEntity<?> excelToPdf(
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal UserDetails userDetails) throws Exception {

        if (file.getSize() > ASYNC_THRESHOLD_BYTES) {
            return submitAsync(file, "EXCEL_TO_PDF", userDetails);
        }
        byte[] result = conversionService.officeToPdf(file.getBytes(), "xlsx");
        return fileResponse(result, "converted.pdf", "application/pdf");
    }

    @PostMapping("/api/v1/convert/ppt-to-pdf")
    @Operation(summary = "PowerPoint → PDF via LibreOffice headless")
    @CheckQuota(feature = FeatureType.CONVERSION)
    public ResponseEntity<?> pptToPdf(
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal UserDetails userDetails) throws Exception {

        if (file.getSize() > ASYNC_THRESHOLD_BYTES) {
            return submitAsync(file, "PPT_TO_PDF", userDetails);
        }
        byte[] result = conversionService.officeToPdf(file.getBytes(), "pptx");
        return fileResponse(result, "converted.pdf", "application/pdf");
    }

    // =========================================================================
    // Manipulation PDF
    // =========================================================================

    @PostMapping("/api/v1/pdf/merge")
    @Operation(summary = "Fusionner plusieurs PDFs en un seul")
    @CheckQuota(feature = FeatureType.CONVERSION)
    public ResponseEntity<?> merge(
            @RequestParam("files") List<MultipartFile> files,
            @AuthenticationPrincipal UserDetails userDetails) throws Exception {

        List<byte[]> pdfs = new java.util.ArrayList<>();
        for (MultipartFile f : files) pdfs.add(f.getBytes());
        byte[] result = pdfManipulationService.merge(pdfs);
        return fileResponse(result, "merged.pdf", "application/pdf");
    }

    @PostMapping("/api/v1/pdf/split")
    @Operation(summary = "Découper un PDF par plages de pages (ex: ?pages=1-3,4-6)")
    @CheckQuota(feature = FeatureType.CONVERSION)
    public ResponseEntity<?> split(
            @RequestParam("file") MultipartFile file,
            @RequestParam String pages,
            @AuthenticationPrincipal UserDetails userDetails) throws Exception {

        int[][] ranges = parsePageRanges(pages);
        List<byte[]> parts = pdfManipulationService.split(file.getBytes(), ranges);
        if (parts.size() == 1) {
            return fileResponse(parts.get(0), "split_1.pdf", "application/pdf");
        }
        byte[] zip = zipPdfs(parts);
        return fileResponse(zip, "split_parts.zip", "application/zip");
    }

    @PostMapping("/api/v1/pdf/compress")
    @Operation(summary = "Compresser un PDF (SCREEN / EBOOK / PRINTER)")
    @CheckQuota(feature = FeatureType.CONVERSION)
    public ResponseEntity<?> compress(
            @RequestParam("file") MultipartFile file,
            @RequestParam(defaultValue = "EBOOK") CompressionLevel level,
            @AuthenticationPrincipal UserDetails userDetails) throws Exception {

        byte[] result = pdfManipulationService.compress(file.getBytes(), level);
        return fileResponse(result, "compressed.pdf", "application/pdf");
    }

    @PostMapping("/api/v1/pdf/rotate")
    @Operation(summary = "Rotation d'une page PDF (90 / 180 / 270 degrés)")
    @CheckQuota(feature = FeatureType.CONVERSION)
    public ResponseEntity<?> rotate(
            @RequestParam("file") MultipartFile file,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "90") int degrees,
            @AuthenticationPrincipal UserDetails userDetails) throws Exception {

        byte[] result = pdfManipulationService.rotate(file.getBytes(), page, degrees);
        return fileResponse(result, "rotated.pdf", "application/pdf");
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    /** Construit une ResponseEntity avec les headers Content-Disposition corrects. */
    private ResponseEntity<byte[]> fileResponse(byte[] data, String filename, String contentType) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, contentType)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(filename).build().toString())
                .header(HttpHeaders.CONTENT_LENGTH, String.valueOf(data.length))
                .body(data);
    }

    /** Délègue le traitement en job async et retourne 202 + jobId. */
    private ResponseEntity<Map<String, Object>> submitAsync(
            MultipartFile file, String operation, UserDetails userDetails) throws Exception {
        Long userId = resolveUserId(userDetails);

        // 1. Stocker le fichier source dans le stockage (MinIO/local)
        String inputKey = "conversions/tmp/" + userId + "/" + java.util.UUID.randomUUID()
                + "/" + file.getOriginalFilename();
        fileStorageService.store(file, inputKey);

        // 2. Créer un ConversionRecord en PENDING
        String inputFmt = getExtension(file.getOriginalFilename()).toUpperCase();
        String outputFmt = getOutputFormat(operation).toUpperCase();
        ConversionRecord record = ConversionRecord.builder()
                .userId(userId)
                .inputFormat(inputFmt)
                .outputFormat(outputFmt)
                .operation(operation)
                .inputSizeBytes(file.getSize())
                .status("PENDING")
                .build();
        record = conversionRepository.save(record);

        // 3. Soumettre le job async avec la clé du fichier source
        String payload = "{\"operation\":\"" + operation
                + "\",\"inputKey\":\"" + inputKey
                + "\",\"recordId\":\"" + record.getId()
                + "\",\"filename\":\"" + file.getOriginalFilename() + "\"}";
        java.util.UUID jobId = aiJobService.submitJob(AiJobType.CONVERSION, null, userId, payload);

        // 4. Mettre à jour le record avec le jobId
        record.setJobId(jobId);
        conversionRepository.save(record);

        log.info("Conversion {} déléguée en job async — jobId={}, recordId={}, user={}",
                operation, jobId, record.getId(), userDetails.getUsername());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of(
                "jobId", jobId.toString(),
                "recordId", record.getId().toString(),
                "status", "PENDING",
                "message", "Fichier volumineux (>10 MB) — traitement asynchrone. "
                         + "Suivez l'avancement via GET /api/v1/jobs/" + jobId
        ));
    }

    private String getExtension(String filename) {
        if (filename == null || !filename.contains(".")) return "bin";
        return filename.substring(filename.lastIndexOf('.') + 1);
    }

    private String getOutputFormat(String operation) {
        return switch (operation) {
            case "TO_WORD"  -> "docx";
            case "TO_EXCEL" -> "xlsx";
            case "TO_IMAGES" -> "zip";
            case "WORD_TO_PDF", "EXCEL_TO_PDF", "PPT_TO_PDF", "MERGE", "COMPRESS" -> "pdf";
            default -> "bin";
        };
    }

    /** Parse "1-3,4-6" → {{1,3},{4,6}}. */
    private int[][] parsePageRanges(String pages) {
        String[] parts = pages.split(",");
        int[][] ranges = new int[parts.length][2];
        for (int i = 0; i < parts.length; i++) {
            String[] bounds = parts[i].trim().split("-");
            if (bounds.length != 2) {
                throw new NexiloException(ErrorCode.VALIDATION_ERROR, HttpStatus.BAD_REQUEST,
                        "Format de plage invalide : '" + parts[i] + "'. Attendu : '1-3'");
            }
            ranges[i][0] = Integer.parseInt(bounds[0].trim());
            ranges[i][1] = Integer.parseInt(bounds[1].trim());
        }
        return ranges;
    }

    private byte[] zipImages(List<byte[]> images, String format) throws Exception {
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        try (java.util.zip.ZipOutputStream zos = new java.util.zip.ZipOutputStream(baos)) {
            for (int i = 0; i < images.size(); i++) {
                zos.putNextEntry(new java.util.zip.ZipEntry("page_" + (i + 1) + "." + format));
                zos.write(images.get(i));
                zos.closeEntry();
            }
        }
        return baos.toByteArray();
    }

    private byte[] zipPdfs(List<byte[]> pdfs) throws Exception {
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        try (java.util.zip.ZipOutputStream zos = new java.util.zip.ZipOutputStream(baos)) {
            for (int i = 0; i < pdfs.size(); i++) {
                zos.putNextEntry(new java.util.zip.ZipEntry("part_" + (i + 1) + ".pdf"));
                zos.write(pdfs.get(i));
                zos.closeEntry();
            }
        }
        return baos.toByteArray();
    }

    private Long resolveUserId(UserDetails userDetails) {
        if (userDetails == null) throw new NexiloException(
                ErrorCode.INVALID_CREDENTIALS, HttpStatus.UNAUTHORIZED, "Non authentifié");
        return userRepository.findByEmail(userDetails.getUsername())
                .map(u -> u.getId())
                .orElseThrow(() -> new NexiloException(
                        ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND,
                        "Utilisateur introuvable : " + userDetails.getUsername()));
    }
}

