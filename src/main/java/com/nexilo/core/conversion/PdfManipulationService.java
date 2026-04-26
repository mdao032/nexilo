package com.nexilo.core.conversion;

import com.nexilo.common.exception.ErrorCode;
import com.nexilo.common.exception.NexiloException;
import lombok.extern.slf4j.Slf4j;
import net.coobird.thumbnailator.Thumbnails;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageTree;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.io.RandomAccessReadBuffer;
import org.apache.pdfbox.multipdf.PDFMergerUtility;
import org.apache.pdfbox.multipdf.Splitter;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Service de manipulation de fichiers PDF existants.
 *
 * <p>Opérations :
 * <ul>
 *   <li>Fusion (merge) de plusieurs PDFs</li>
 *   <li>Découpage (split) par plages de pages</li>
 *   <li>Compression des images embarquées (SCREEN / EBOOK / PRINTER)</li>
 *   <li>Rotation d'une page</li>
 *   <li>Extraction d'un intervalle de pages</li>
 * </ul>
 */
@Slf4j
@Service
public class PdfManipulationService {

    // =========================================================================
    // Fusion
    // =========================================================================

    /**
     * Fusionne plusieurs PDFs en un seul document.
     *
     * @param pdfs liste des PDFs à assembler (dans l'ordre)
     * @return PDF fusionné
     */
    public byte[] merge(List<byte[]> pdfs) {
        if (pdfs == null || pdfs.isEmpty()) {
            throw new NexiloException(ErrorCode.VALIDATION_ERROR, HttpStatus.BAD_REQUEST,
                    "Au moins un PDF est requis pour la fusion");
        }
        log.info("Fusion de {} PDFs", pdfs.size());
        try {
            PDFMergerUtility merger = new PDFMergerUtility();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            merger.setDestinationStream(out);
            for (byte[] pdf : pdfs) {
                merger.addSource(new RandomAccessReadBuffer(pdf));
            }
            merger.mergeDocuments(null);
            log.info("Fusion terminée — {} bytes", out.size());
            return out.toByteArray();
        } catch (Exception e) {
            throw new NexiloException(ErrorCode.PROCESSING_ERROR, HttpStatus.UNPROCESSABLE_ENTITY,
                    "Erreur lors de la fusion des PDFs : " + e.getMessage(), e);
        }
    }

    // =========================================================================
    // Découpage
    // =========================================================================

    /**
     * Découpe un PDF selon des plages de pages.
     *
     * @param pdf        PDF source
     * @param pageRanges tableau de paires [début, fin] (1-indexed, inclus)
     *                   ex: {{1,3},{4,6}} → deux documents de 3 pages chacun
     * @return liste de PDFs découpés
     */
    public List<byte[]> split(byte[] pdf, int[][] pageRanges) {
        if (pageRanges == null || pageRanges.length == 0) {
            throw new NexiloException(ErrorCode.VALIDATION_ERROR, HttpStatus.BAD_REQUEST,
                    "Au moins une plage de pages est requise");
        }
        log.info("Découpage PDF en {} parties", pageRanges.length);
        List<byte[]> results = new ArrayList<>();
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            int totalPages = doc.getNumberOfPages();
            for (int[] range : pageRanges) {
                int from = Math.max(1, range[0]);
                int to = Math.min(totalPages, range[1]);
                results.add(extractPages(doc, from, to));
            }
        } catch (NexiloException e) {
            throw e;
        } catch (Exception e) {
            throw new NexiloException(ErrorCode.PROCESSING_ERROR, HttpStatus.UNPROCESSABLE_ENTITY,
                    "Erreur lors du découpage : " + e.getMessage(), e);
        }
        log.info("Découpage terminé — {} documents générés", results.size());
        return results;
    }

    // =========================================================================
    // Compression
    // =========================================================================

    /**
     * Compresse les images embarquées dans un PDF.
     * Réduit significativement la taille des PDFs issus de scans.
     *
     * @param pdf   PDF source
     * @param level niveau de compression (SCREEN 72dpi / EBOOK 150dpi / PRINTER 300dpi)
     * @return PDF compressé
     */
    public byte[] compress(byte[] pdf, CompressionLevel level) {
        if (level == null) level = CompressionLevel.EBOOK;
        log.info("Compression PDF — niveau={} ({}dpi), taille initiale={} bytes",
                level, level.dpi, pdf.length);
        try (PDDocument doc = Loader.loadPDF(pdf);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            compressImages(doc, level.dpi);
            doc.save(out);

            log.info("Compression terminée — {} bytes → {} bytes ({:.1f}% réduction)",
                    pdf.length, out.size(), 100.0 * (pdf.length - out.size()) / pdf.length);
            return out.toByteArray();
        } catch (Exception e) {
            throw new NexiloException(ErrorCode.PROCESSING_ERROR, HttpStatus.UNPROCESSABLE_ENTITY,
                    "Erreur lors de la compression : " + e.getMessage(), e);
        }
    }

    // =========================================================================
    // Rotation
    // =========================================================================

    /**
     * Applique une rotation à une page spécifique.
     *
     * @param pdf       PDF source
     * @param pageIndex index de la page (0-indexed)
     * @param degrees   rotation : 90, 180 ou 270
     * @return PDF avec la page tournée
     */
    public byte[] rotate(byte[] pdf, int pageIndex, int degrees) {
        if (degrees % 90 != 0) {
            throw new NexiloException(ErrorCode.VALIDATION_ERROR, HttpStatus.BAD_REQUEST,
                    "La rotation doit être un multiple de 90° (90, 180, 270)");
        }
        log.info("Rotation page {} de {}°", pageIndex, degrees);
        try (PDDocument doc = Loader.loadPDF(pdf);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            if (pageIndex < 0 || pageIndex >= doc.getNumberOfPages()) {
                throw new NexiloException(ErrorCode.VALIDATION_ERROR, HttpStatus.BAD_REQUEST,
                        "Index de page invalide : " + pageIndex + " (total: " + doc.getNumberOfPages() + ")");
            }
            PDPage page = doc.getPage(pageIndex);
            int current = page.getRotation();
            page.setRotation((current + degrees) % 360);
            doc.save(out);
            return out.toByteArray();
        } catch (NexiloException e) {
            throw e;
        } catch (Exception e) {
            throw new NexiloException(ErrorCode.PROCESSING_ERROR, HttpStatus.UNPROCESSABLE_ENTITY,
                    "Erreur lors de la rotation : " + e.getMessage(), e);
        }
    }

    // =========================================================================
    // Extraction de pages
    // =========================================================================

    /**
     * Extrait un intervalle de pages d'un PDF.
     *
     * @param pdf  PDF source
     * @param from première page (1-indexed, incluse)
     * @param to   dernière page (1-indexed, incluse)
     * @return PDF contenant uniquement les pages demandées
     */
    public byte[] extractPages(byte[] pdf, int from, int to) {
        log.info("Extraction pages {}-{}", from, to);
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            return extractPages(doc, from, to);
        } catch (NexiloException e) {
            throw e;
        } catch (Exception e) {
            throw new NexiloException(ErrorCode.PROCESSING_ERROR, HttpStatus.UNPROCESSABLE_ENTITY,
                    "Erreur lors de l'extraction de pages : " + e.getMessage(), e);
        }
    }

    // =========================================================================
    // Helpers privés
    // =========================================================================

    private byte[] extractPages(PDDocument source, int from, int to) throws IOException {
        int total = source.getNumberOfPages();
        int safeFrom = Math.max(1, from);
        int safeTo = Math.min(total, to);

        if (safeFrom > safeTo) {
            throw new NexiloException(ErrorCode.VALIDATION_ERROR, HttpStatus.BAD_REQUEST,
                    "Plage de pages invalide : " + from + "-" + to + " (total: " + total + ")");
        }

        try (PDDocument extracted = new PDDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            for (int i = safeFrom - 1; i < safeTo; i++) {
                extracted.addPage(source.getPage(i));
            }
            extracted.save(out);
            return out.toByteArray();
        }
    }

    /**
     * Recompresse toutes les images XObject du document avec Thumbnailator.
     */
    private void compressImages(PDDocument doc, int targetDpi) throws IOException {
        PDPageTree pages = doc.getPages();
        for (PDPage page : pages) {
            var resources = page.getResources();
            if (resources == null) continue;
            for (COSName name : resources.getXObjectNames()) {
                try {
                    var xobj = resources.getXObject(name);
                    if (!(xobj instanceof PDImageXObject img)) continue;

                    BufferedImage original = img.getImage();
                    if (original == null) continue;

                    // Calculer le facteur de réduction
                    float scale = Math.min(1.0f, targetDpi / 300.0f);
                    int newW = Math.max(1, (int) (original.getWidth() * scale));
                    int newH = Math.max(1, (int) (original.getHeight() * scale));

                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    Thumbnails.of(original)
                              .size(newW, newH)
                              .outputFormat("JPEG")
                              .outputQuality(0.75f)
                              .toOutputStream(baos);

                    PDImageXObject compressed = PDImageXObject.createFromByteArray(
                            doc, baos.toByteArray(), name.getName());
                    resources.put(name, compressed);
                } catch (Exception ignored) {
                    // Ignorer les images qui ne peuvent pas être recompressées
                }
            }
        }
    }
}

