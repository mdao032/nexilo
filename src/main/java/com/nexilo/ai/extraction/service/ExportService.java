package com.nexilo.ai.extraction.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexilo.ai.extraction.entity.ExtractionResultEntity;
import com.nexilo.common.exception.ErrorCode;
import com.nexilo.common.exception.NexiloException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Service d'export des resultats d'extraction en JSON, CSV et Excel (XLSX).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExportService {

    private static final String NEXILO_BLUE = "2563EB";
    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ObjectMapper objectMapper;

    /** Exporte un resultat d'extraction en JSON formate (pretty-print). */
    public byte[] toJson(ExtractionResultEntity entity) {
        try {
            Map<String, Object> export = buildExportMap(entity);
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(export);
        } catch (Exception e) {
            throw new NexiloException(ErrorCode.PROCESSING_ERROR, HttpStatus.INTERNAL_SERVER_ERROR,
                "Erreur export JSON: " + e.getMessage(), e);
        }
    }

    /** Exporte un resultat d'extraction en CSV (BOM UTF-8 pour Excel). */
    public byte[] toCsv(ExtractionResultEntity entity) {
        try {
            Map<String, Object> result = parseResult(entity);
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            // En-tetes
            pw.println("Champ,Valeur,Type");
            result.forEach((k, v) -> {
                String val = v == null ? "" : v.toString().replace("\"", "\"\"\"");
                if (val.contains(",") || val.contains("\n")) val = "\"" + val + "\"";
                pw.println(k + "," + val + "," + guessType(v));
            });
            // Separateur metadonnees
            pw.println();
            pw.println("--- Metadonnees ---");
            pw.println("Document ID," + entity.getDocument().getId());
            pw.println("Template," + (entity.getTemplate() != null ? entity.getTemplate().getName() : "CUSTOM"));
            pw.println("Confiance," + String.format("%.0f%%", (entity.getConfidence() != null ? entity.getConfidence() : 0) * 100));
            pw.println("Modele," + entity.getModel());
            pw.println("Date," + (entity.getCreatedAt() != null ? entity.getCreatedAt().format(DT_FMT) : ""));
            pw.flush();
            // BOM UTF-8 pour Excel
            byte[] bom = new byte[]{(byte)0xEF, (byte)0xBB, (byte)0xBF};
            byte[] content = sw.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
            byte[] result2 = new byte[bom.length + content.length];
            System.arraycopy(bom, 0, result2, 0, bom.length);
            System.arraycopy(content, 0, result2, bom.length, content.length);
            return result2;
        } catch (Exception e) {
            throw new NexiloException(ErrorCode.PROCESSING_ERROR, HttpStatus.INTERNAL_SERVER_ERROR,
                "Erreur export CSV: " + e.getMessage(), e);
        }
    }

    /** Exporte en Excel XLSX : feuille Resultat + feuille Metadonnees, en-tete bleu Nexilo. */
    public byte[] toExcel(ExtractionResultEntity entity) {
        try (XSSFWorkbook wb = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            // --- Styles ---
            XSSFCellStyle headerStyle = createHeaderStyle(wb);
            XSSFCellStyle metaLabelStyle = createMetaLabelStyle(wb);
            XSSFCellStyle valueStyle = createValueStyle(wb);

            // === Feuille 1 : Resultat ===
            XSSFSheet sheetResult = wb.createSheet("Resultat");
            XSSFRow headerRow = sheetResult.createRow(0);
            String[] cols = {"Champ", "Valeur", "Type"};
            for (int i = 0; i < cols.length; i++) {
                XSSFCell cell = headerRow.createCell(i);
                cell.setCellValue(cols[i]);
                cell.setCellStyle(headerStyle);
            }
            Map<String, Object> fields = parseResult(entity);
            int rowIdx = 1;
            for (Map.Entry<String, Object> entry : fields.entrySet()) {
                XSSFRow row = sheetResult.createRow(rowIdx++);
                row.createCell(0).setCellValue(entry.getKey());
                XSSFCell valCell = row.createCell(1);
                valCell.setCellStyle(valueStyle);
                if (entry.getValue() == null) { valCell.setCellValue(""); }
                else if (entry.getValue() instanceof Number n) { valCell.setCellValue(n.doubleValue()); }
                else if (entry.getValue() instanceof Boolean b) { valCell.setCellValue(b); }
                else { valCell.setCellValue(entry.getValue().toString()); }
                row.createCell(2).setCellValue(guessType(entry.getValue()));
            }
            sheetResult.autoSizeColumn(0);
            sheetResult.autoSizeColumn(1);
            sheetResult.autoSizeColumn(2);

            // === Feuille 2 : Metadonnees ===
            XSSFSheet sheetMeta = wb.createSheet("Metadonnees");
            String[][] meta = {
                {"ID Extraction", entity.getId().toString()},
                {"Document ID", entity.getDocument().getId().toString()},
                {"Template", entity.getTemplate() != null ? entity.getTemplate().getName() : "CUSTOM"},
                {"Modele IA", entity.getModel() != null ? entity.getModel() : ""},
                {"Tokens utilises", entity.getTokensUsed() != null ? entity.getTokensUsed().toString() : "0"},
                {"Confiance", entity.getConfidence() != null ? String.format("%.0f%%", entity.getConfidence() * 100) : "N/A"},
                {"Date extraction", entity.getCreatedAt() != null ? entity.getCreatedAt().format(DT_FMT) : ""}
            };
            XSSFRow metaHeader = sheetMeta.createRow(0);
            metaHeader.createCell(0).setCellValue("Propriete");
            metaHeader.getCell(0).setCellStyle(headerStyle);
            metaHeader.createCell(1).setCellValue("Valeur");
            metaHeader.getCell(1).setCellStyle(headerStyle);
            for (int i = 0; i < meta.length; i++) {
                XSSFRow r = sheetMeta.createRow(i + 1);
                XSSFCell lbl = r.createCell(0);
                lbl.setCellValue(meta[i][0]); lbl.setCellStyle(metaLabelStyle);
                r.createCell(1).setCellValue(meta[i][1]);
            }
            sheetMeta.autoSizeColumn(0);
            sheetMeta.autoSizeColumn(1);

            wb.write(out);
            log.info("Export Excel genere - extractionId={}", entity.getId());
            return out.toByteArray();
        } catch (Exception e) {
            throw new NexiloException(ErrorCode.PROCESSING_ERROR, HttpStatus.INTERNAL_SERVER_ERROR,
                "Erreur export Excel: " + e.getMessage(), e);
        }
    }

    // ==========================================================================
    // Helpers
    // ==========================================================================

    private XSSFCellStyle createHeaderStyle(XSSFWorkbook wb) {
        XSSFCellStyle style = wb.createCellStyle();
        style.setFillForegroundColor(new XSSFColor(java.awt.Color.decode("#" + NEXILO_BLUE), new DefaultIndexedColorMap()));
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        XSSFFont font = wb.createFont();
        font.setColor(new XSSFColor(java.awt.Color.WHITE, new DefaultIndexedColorMap()));
        font.setBold(true);
        style.setFont(font);
        style.setBorderBottom(BorderStyle.THIN);
        return style;
    }

    private XSSFCellStyle createMetaLabelStyle(XSSFWorkbook wb) {
        XSSFCellStyle style = wb.createCellStyle();
        XSSFFont font = wb.createFont();
        font.setBold(true);
        style.setFont(font);
        return style;
    }

    private XSSFCellStyle createValueStyle(XSSFWorkbook wb) {
        XSSFCellStyle style = wb.createCellStyle();
        style.setWrapText(true);
        return style;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseResult(ExtractionResultEntity entity) {
        try {
            Object parsed = objectMapper.readValue(entity.getResult(), Object.class);
            if (parsed instanceof Map<?, ?> m) {
                Map<String, Object> result = new LinkedHashMap<>();
                m.forEach((k, v) -> result.put(k.toString(), v));
                return result;
            }
            return new LinkedHashMap<>();
        } catch (Exception e) { return new LinkedHashMap<>(); }
    }

    private Map<String, Object> buildExportMap(ExtractionResultEntity entity) {
        Map<String, Object> export = new LinkedHashMap<>();
        export.put("extractionId", entity.getId());
        export.put("documentId", entity.getDocument().getId());
        export.put("template", entity.getTemplate() != null ? entity.getTemplate().getName() : "CUSTOM");
        export.put("model", entity.getModel());
        export.put("confidence", entity.getConfidence());
        export.put("createdAt", entity.getCreatedAt());
        export.put("fields", parseResult(entity));
        return export;
    }

    private String guessType(Object value) {
        if (value == null) return "NULL";
        if (value instanceof Number) return "NUMBER";
        if (value instanceof Boolean) return "BOOLEAN";
        if (value instanceof java.util.List) return "LIST";
        String s = value.toString();
        if (s.matches("\\d{4}-\\d{2}-\\d{2}.*")) return "DATE";
        return "STRING";
    }
}
