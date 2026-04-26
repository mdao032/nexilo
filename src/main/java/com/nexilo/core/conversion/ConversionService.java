package com.nexilo.core.conversion;

import com.nexilo.common.exception.ErrorCode;
import com.nexilo.common.exception.NexiloException;
import com.nexilo.usage.UsageService;
import com.nexilo.user.entity.PlanConfig;
import com.nexilo.user.entity.FeatureType;
import com.nexilo.user.entity.UserPlan;
import com.nexilo.user.quota.CheckQuota;
import com.nexilo.user.repository.UserRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Service de conversion de fichiers avec quota par plan, métriques Micrometer
 * et tracking d'usage.
 *
 * <p>Conversions supportées :
 * <ul>
 *   <li>PDF → Word (DOCX) — extraction texte PDFBox + reconstruction POI</li>
 *   <li>PDF → Images (PNG/JPG) — rendu PDFRenderer à DPI configurable</li>
 *   <li>PDF → Excel (XLSX) — extraction tabulaire simple</li>
 *   <li>Images → PDF — PDFBox PDImageXObject</li>
 *   <li>Word/Excel/PPT → PDF — LibreOffice headless (PRO+ uniquement)</li>
 * </ul>
 *
 * <p>Métriques publiées :
 * <ul>
 *   <li>{@code nexilo.conversions.total}        — counter (inputFormat, outputFormat, plan)</li>
 *   <li>{@code nexilo.conversions.duration}      — histogram en ms</li>
 *   <li>{@code nexilo.conversions.file_size}     — histogram en bytes (taille source)</li>
 *   <li>{@code nexilo.storage.bytes_generated}   — counter octets générés</li>
 * </ul>
 */
@Slf4j
@Service
public class ConversionService {

    private final LibreOfficeConfig libreOfficeConfig;
    private final UsageService usageService;
    private final UserRepository userRepository;
    private final MeterRegistry meterRegistry;

    // Compteur global de fichiers générés (bytes)
    private final Counter storageBytesGenerated;

    public ConversionService(LibreOfficeConfig libreOfficeConfig,
                             UsageService usageService,
                             UserRepository userRepository,
                             MeterRegistry meterRegistry) {
        this.libreOfficeConfig = libreOfficeConfig;
        this.usageService      = usageService;
        this.userRepository    = userRepository;
        this.meterRegistry     = meterRegistry;
        this.storageBytesGenerated = Counter.builder("nexilo.storage.bytes_generated")
                .description("Volume total de fichiers convertis générés en octets")
                .register(meterRegistry);
    }

    // =========================================================================
    // PDF → Word
    // =========================================================================

    /**
     * Convertit un PDF en document Word (.docx).
     * PDFBox extrait le texte paragraphe par paragraphe ; Apache POI reconstruit le DOCX.
     */
    @CheckQuota(feature = FeatureType.CONVERSION)
    public byte[] pdfToWord(byte[] pdf) {
        return timed("PDF", "DOCX", pdf.length, () -> {
            log.info("Conversion PDF→DOCX ({} bytes)", pdf.length);
            try (PDDocument doc = Loader.loadPDF(pdf);
                 XWPFDocument wordDoc = new XWPFDocument();
                 ByteArrayOutputStream out = new ByteArrayOutputStream()) {

                PDFTextStripper stripper = new PDFTextStripper();
                stripper.setParagraphStart("\n\n");
                String text = stripper.getText(doc);

                // Créer un paragraphe POI par bloc de texte
                for (String block : text.split("\n\n")) {
                    String trimmed = block.trim();
                    if (trimmed.isEmpty()) continue;
                    XWPFParagraph para = wordDoc.createParagraph();
                    XWPFRun run = para.createRun();
                    run.setText(trimmed);
                    run.addBreak();
                }

                wordDoc.write(out);
                log.info("PDF→DOCX terminé — {} bytes générés", out.size());
                return out.toByteArray();
            } catch (Exception e) {
                throw new NexiloException(ErrorCode.PROCESSING_ERROR, HttpStatus.UNPROCESSABLE_ENTITY,
                        "Erreur PDF→DOCX : " + e.getMessage(), e);
            }
        });
    }

    // =========================================================================
    // PDF → Images
    // =========================================================================

    /**
     * Convertit chaque page d'un PDF en image.
     *
     * @param pdf    contenu du PDF
     * @param format "png" (défaut, sans perte) ou "jpg" (plus léger)
     * @param dpi    résolution : 150 (web) ou 300 (impression)
     * @return liste d'images (une par page)
     */
    @CheckQuota(feature = FeatureType.CONVERSION)
    public List<byte[]> pdfToImages(byte[] pdf, String format, int dpi) {
        String fmt    = (format == null || format.isBlank()) ? "png" : format.toLowerCase();
        int safeDpi   = (dpi <= 0 || dpi > 600) ? 150 : dpi;
        long start    = System.currentTimeMillis();

        log.info("Conversion PDF→Images (format={}, dpi={})", fmt, safeDpi);
        List<byte[]> images = new ArrayList<>();
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            PDFRenderer renderer = new PDFRenderer(doc);
            for (int i = 0; i < doc.getNumberOfPages(); i++) {
                BufferedImage img = renderer.renderImageWithDPI(i, safeDpi,
                        "jpg".equals(fmt) ? ImageType.RGB : ImageType.ARGB);
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                ImageIO.write(img, fmt, baos);
                images.add(baos.toByteArray());
            }
            long duration = System.currentTimeMillis() - start;
            long totalOut = images.stream().mapToLong(b -> b.length).sum();

            recordMetrics("PDF", fmt.toUpperCase(), pdf.length, totalOut, duration);
            log.info("PDF→Images terminé — {} images, {}ms", images.size(), duration);
            return images;
        } catch (Exception e) {
            throw new NexiloException(ErrorCode.PROCESSING_ERROR, HttpStatus.UNPROCESSABLE_ENTITY,
                    "Erreur PDF→Images : " + e.getMessage(), e);
        }
    }

    // =========================================================================
    // PDF → Excel
    // =========================================================================

    /**
     * Extrait le texte du PDF et tente de reconstruire la structure tabulaire en XLSX.
     */
    @CheckQuota(feature = FeatureType.CONVERSION)
    public byte[] pdfToExcel(byte[] pdf) {
        return timed("PDF", "XLSX", pdf.length, () -> {
            log.info("Conversion PDF→XLSX ({} bytes)", pdf.length);
            try (PDDocument doc = Loader.loadPDF(pdf)) {
                PDFTextStripper stripper = new PDFTextStripper();
                String text = stripper.getText(doc);

                try (org.apache.poi.xssf.usermodel.XSSFWorkbook wb = new org.apache.poi.xssf.usermodel.XSSFWorkbook();
                     ByteArrayOutputStream out = new ByteArrayOutputStream()) {

                    org.apache.poi.ss.usermodel.Sheet sheet = wb.createSheet("Extraction");
                    int rowNum = 0;
                    for (String line : text.split("\n")) {
                        if (line.isBlank()) continue;
                        org.apache.poi.ss.usermodel.Row row = sheet.createRow(rowNum++);
                        // Découpe sur 2+ espaces consécutifs (heuristique colonnes)
                        String[] cells = line.trim().split("\\s{2,}");
                        for (int c = 0; c < cells.length; c++) {
                            row.createCell(c).setCellValue(cells[c].trim());
                        }
                    }
                    wb.write(out);
                    log.info("PDF→XLSX terminé — {} lignes, {} bytes", rowNum, out.size());
                    return out.toByteArray();
                }
            } catch (Exception e) {
                throw new NexiloException(ErrorCode.PROCESSING_ERROR, HttpStatus.UNPROCESSABLE_ENTITY,
                        "Erreur PDF→XLSX : " + e.getMessage(), e);
            }
        });
    }

    // =========================================================================
    // Images → PDF
    // =========================================================================

    /**
     * Assemble une liste d'images (JPG, PNG, WEBP) en un seul PDF.
     */
    @CheckQuota(feature = FeatureType.CONVERSION)
    public byte[] imagesToPdf(List<MultipartFile> images) {
        return timed("IMAGE", "PDF", images.stream().mapToLong(f -> f.getSize()).sum(), () -> {
            log.info("Conversion Images→PDF ({} images)", images.size());
            try (PDDocument doc = new PDDocument();
                 ByteArrayOutputStream out = new ByteArrayOutputStream()) {

                for (MultipartFile img : images) {
                    byte[] imgBytes = img.getBytes();
                    PDImageXObject pdImage = PDImageXObject.createFromByteArray(doc, imgBytes,
                            img.getOriginalFilename());
                    PDPage page = new PDPage(new PDRectangle(pdImage.getWidth(), pdImage.getHeight()));
                    doc.addPage(page);
                    try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                        cs.drawImage(pdImage, 0, 0, pdImage.getWidth(), pdImage.getHeight());
                    }
                }

                doc.save(out);
                log.info("Images→PDF terminé — {} bytes", out.size());
                return out.toByteArray();
            } catch (Exception e) {
                throw new NexiloException(ErrorCode.PROCESSING_ERROR, HttpStatus.UNPROCESSABLE_ENTITY,
                        "Erreur Images→PDF : " + e.getMessage(), e);
            }
        });
    }

    // =========================================================================
    // Word / Excel / PPT → PDF via LibreOffice headless (PRO+ uniquement)
    // =========================================================================

    /**
     * Convertit un fichier Office (DOCX, XLSX, PPTX) en PDF via LibreOffice headless.
     * Réservé aux plans PRO et ENTERPRISE ({@code libreOfficeEnabled = true}).
     *
     * @param content   contenu du fichier Office
     * @param extension extension source ("docx", "xlsx", "pptx")
     * @return PDF généré
     */
    @CheckQuota(feature = FeatureType.CONVERSION)
    public byte[] officeToPdf(byte[] content, String extension) {
        // Vérification plan LibreOffice (PRO+)
        checkLibreOfficePlan();

        if (!libreOfficeConfig.isAvailable()) {
            throw new NexiloException(ErrorCode.PROCESSING_ERROR, HttpStatus.SERVICE_UNAVAILABLE,
                    "LibreOffice n'est pas installé sur ce serveur. Contactez le support.");
        }

        return timed(extension.toUpperCase(), "PDF", content.length, () -> {
            log.info("Conversion {}→PDF via LibreOffice ({} bytes)", extension.toUpperCase(), content.length);
            Path tmpDir = null;
            try {
                tmpDir = Files.createTempDirectory("nexilo-lo-");
                Path inputFile = tmpDir.resolve("input." + extension);
                Files.write(inputFile, content);

                ProcessBuilder pb = new ProcessBuilder(
                        libreOfficeConfig.getLibreOfficePath(),
                        "--headless",
                        "--convert-to", "pdf",
                        "--outdir", tmpDir.toString(),
                        inputFile.toString()
                );
                pb.redirectErrorStream(true);
                Process process = pb.start();
                String output = new String(process.getInputStream().readAllBytes());
                int exitCode = process.waitFor();

                if (exitCode != 0) {
                    throw new NexiloException(ErrorCode.PROCESSING_ERROR, HttpStatus.UNPROCESSABLE_ENTITY,
                            "LibreOffice a échoué (code " + exitCode + ") : " + output);
                }

                Path outputPdf = tmpDir.resolve("input.pdf");
                if (!Files.exists(outputPdf)) {
                    throw new NexiloException(ErrorCode.PROCESSING_ERROR, HttpStatus.INTERNAL_SERVER_ERROR,
                            "LibreOffice n'a pas généré le PDF. Sortie : " + output);
                }

                byte[] result = Files.readAllBytes(outputPdf);
                log.info("{}→PDF terminé — {} bytes", extension.toUpperCase(), result.length);
                return result;

            } catch (NexiloException e) {
                throw e;
            } catch (Exception e) {
                throw new NexiloException(ErrorCode.PROCESSING_ERROR, HttpStatus.INTERNAL_SERVER_ERROR,
                        "Erreur " + extension + "→PDF : " + e.getMessage(), e);
            } finally {
                if (tmpDir != null) {
                    try {
                        try (var walk = Files.walk(tmpDir)) {
                            walk.sorted(java.util.Comparator.reverseOrder())
                                .forEach(p -> { try { Files.deleteIfExists(p); } catch (Exception ignored) {} });
                        }
                    } catch (Exception ignored) {}
                }
            }
        });
    }

    // =========================================================================
    // Helpers privés
    // =========================================================================

    /**
     * Wrapper qui mesure la durée, enregistre les métriques et l'usage,
     * puis retourne le résultat de la conversion.
     */
    private byte[] timed(String inputFmt, String outputFmt, long inputSize,
                         ConversionSupplier supplier) {
        long start = System.currentTimeMillis();
        byte[] result;
        try {
            result = supplier.get();
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new NexiloException(ErrorCode.PROCESSING_ERROR, HttpStatus.INTERNAL_SERVER_ERROR,
                    "Erreur de conversion : " + e.getMessage(), e);
        }
        long duration = System.currentTimeMillis() - start;
        recordMetrics(inputFmt, outputFmt, inputSize, result.length, duration);
        return result;
    }

    /**
     * Publie les métriques Micrometer et enregistre l'usage en base.
     */
    private void recordMetrics(String inputFmt, String outputFmt,
                                long inputSize, long outputSize, long durationMs) {
        Long userId = resolveCurrentUserId();
        String plan = resolvePlanName(userId);

        // nexilo.conversions.total — counter par format et plan
        meterRegistry.counter("nexilo.conversions.total",
                "inputFormat", inputFmt,
                "outputFormat", outputFmt,
                "plan", plan)
                .increment();

        // nexilo.conversions.duration — histogram en ms
        meterRegistry.timer("nexilo.conversions.duration",
                "inputFormat", inputFmt,
                "outputFormat", outputFmt)
                .record(durationMs, TimeUnit.MILLISECONDS);

        // nexilo.conversions.file_size — histogram taille source
        DistributionSummary.builder("nexilo.conversions.file_size")
                .description("Taille des fichiers source en octets")
                .baseUnit("bytes")
                .tag("inputFormat", inputFmt)
                .register(meterRegistry)
                .record(inputSize);

        // nexilo.storage.bytes_generated — counter volume généré
        storageBytesGenerated.increment(outputSize);

        // Usage en base (async — ne bloque pas)
        if (userId != null) {
            usageService.recordConversion(userId, inputFmt, outputFmt, inputSize, outputSize, durationMs);
        }
    }

    /**
     * Vérifie que l'utilisateur courant a accès à LibreOffice (plan PRO ou ENTERPRISE).
     */
    private void checkLibreOfficePlan() {
        Long userId = resolveCurrentUserId();
        if (userId == null) return; // sécurité gérée par Spring Security
        UserPlan plan = userRepository.findById(userId)
                .map(u -> u.getPlan() != null ? u.getPlan() : UserPlan.FREE)
                .orElse(UserPlan.FREE);
        PlanConfig config = PlanConfig.forPlan(plan);
        if (!config.libreOfficeEnabled()) {
            throw new NexiloException(ErrorCode.PLAN_LIMIT_EXCEEDED, HttpStatus.PAYMENT_REQUIRED,
                    "La conversion Office→PDF via LibreOffice est réservée au plan PRO. " +
                    "Votre plan actuel : " + plan.name() + ". Passez à PRO sur /pricing.");
        }
    }

    private Long resolveCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return null;
        }
        // Le subject JWT est l'email — on cherche l'ID réel en base
        String email = auth.getName();
        return userRepository.findByEmail(email)
                .map(u -> u.getId())
                .orElse(null);
    }

    private String resolvePlanName(Long userId) {
        if (userId == null) return "UNKNOWN";
        return userRepository.findById(userId)
                .map(u -> u.getPlan() != null ? u.getPlan().name() : "FREE")
                .orElse("FREE");
    }

    // Interface fonctionnelle interne pour le wrapper timed()
    @FunctionalInterface
    private interface ConversionSupplier {
        byte[] get() throws Exception;
    }
}
