package com.nexilo.processing.utils;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import java.io.InputStream;

@Component
public class PdfExtractor {

    /**
     * Extrait le texte d'un flux de données PDF en utilisant Apache PDFBox.
     *
     * @param inputStream le flux de données d'entrée représentant le fichier PDF
     * @return le texte extrait du document
     * @throws RuntimeException si une erreur survient lors de la lecture du flux ou de l'extraction
     */
    public String extract(InputStream inputStream) {
        try (PDDocument document = Loader.loadPDF(inputStream.readAllBytes())) {
            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(document);
        } catch (Exception e) {
            throw new RuntimeException("Error extracting PDF", e);
        }
    }
}
