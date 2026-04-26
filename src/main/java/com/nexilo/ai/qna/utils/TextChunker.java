package com.nexilo.ai.qna.utils;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Utilitaire de découpe de texte en chunks pour l'ingestion RAG.
 *
 * <p>Algorithme :
 * <ol>
 *   <li>Découpe le texte en phrases sur les terminateurs {. ! ? \n\n}</li>
 *   <li>Accumule les phrases jusqu'à atteindre {@code chunkSize} caractères</li>
 *   <li>Applique un overlap : le chunk suivant reprend les dernières phrases
 *       du chunk précédent jusqu'à couvrir {@code overlap} caractères</li>
 *   <li>Filtre les chunks vides ou trop courts (&lt; {@code MIN_CHUNK_LENGTH} chars)</li>
 * </ol>
 */
@Slf4j
@Component
public class TextChunker {

    /** Taille de chunk par défaut (~800 tokens × 4 chars/token). */
    public static final int DEFAULT_CHUNK_SIZE = 3_200;

    /** Overlap par défaut (~100 tokens × 4 chars/token). */
    public static final int DEFAULT_OVERLAP = 400;

    /** Longueur minimale d'un chunk valide. */
    private static final int MIN_CHUNK_LENGTH = 50;

    /**
     * Séparateur de phrases : point/excl./interr. suivi d'un espace ou newline,
     * ou double newline (paragraphe).
     */
    private static final Pattern SENTENCE_BOUNDARY = Pattern.compile(
            "(?<=[.!?])(?=\\s)|(?<=\\n)(?=\\n)"
    );

    /**
     * Découpe un texte en chunks avec overlap sur les fins de phrases.
     *
     * @param text      texte à découper
     * @param chunkSize taille max d'un chunk en caractères
     * @param overlap   nombre de caractères à reprendre du chunk précédent
     * @return liste de chunks non vides et de taille suffisante
     */
    public List<String> chunk(String text, int chunkSize, int overlap) {
        if (text == null || text.isBlank()) {
            return List.of();
        }

        // 1. Découpe en phrases
        String[] sentences = SENTENCE_BOUNDARY.split(text.trim());
        List<String> sentenceList = new ArrayList<>();
        for (String s : sentences) {
            String trimmed = s.strip();
            if (!trimmed.isEmpty()) {
                sentenceList.add(trimmed);
            }
        }

        if (sentenceList.isEmpty()) {
            return List.of();
        }

        List<String> chunks = new ArrayList<>();
        int i = 0;

        while (i < sentenceList.size()) {
            // 2. Accumuler des phrases jusqu'à chunkSize
            StringBuilder current = new StringBuilder();
            int start = i;

            while (i < sentenceList.size()) {
                String sentence = sentenceList.get(i);
                int projected = current.length() + (current.length() > 0 ? 1 : 0) + sentence.length();

                if (projected > chunkSize && current.length() > 0) {
                    break; // on ne dépasse pas la taille du chunk
                }

                if (current.length() > 0) current.append(' ');
                current.append(sentence);
                i++;
            }

            String chunkText = current.toString().strip();
            if (chunkText.length() >= MIN_CHUNK_LENGTH) {
                chunks.add(chunkText);
            }

            // Si on n'a pas avancé (phrase unique plus longue que chunkSize)
            if (i == start) {
                // Forcer l'avancement pour éviter une boucle infinie
                String forced = sentenceList.get(i).strip();
                if (forced.length() >= MIN_CHUNK_LENGTH) {
                    chunks.add(forced.length() > chunkSize
                            ? forced.substring(0, chunkSize).strip()
                            : forced);
                }
                i++;
                continue;
            }

            // 3. Calculer le point de reprise pour l'overlap
            if (i < sentenceList.size() && overlap > 0) {
                i = computeOverlapStart(sentenceList, i, overlap);
            }
        }

        log.debug("TextChunker : {} chars → {} chunks (chunkSize={}, overlap={})",
                text.length(), chunks.size(), chunkSize, overlap);
        return chunks;
    }

    /**
     * Surcharge avec les valeurs par défaut.
     */
    public List<String> chunk(String text) {
        return chunk(text, DEFAULT_CHUNK_SIZE, DEFAULT_OVERLAP);
    }

    /**
     * Remonte depuis {@code currentIndex} vers le début du chunk précédent
     * jusqu'à couvrir au moins {@code overlap} caractères.
     */
    private int computeOverlapStart(List<String> sentences, int currentIndex, int overlap) {
        int accumulated = 0;
        int idx = currentIndex - 1;
        while (idx > 0 && accumulated < overlap) {
            accumulated += sentences.get(idx).length() + 1;
            idx--;
        }
        return Math.max(0, idx + 1);
    }
}

