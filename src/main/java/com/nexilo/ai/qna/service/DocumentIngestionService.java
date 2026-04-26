package com.nexilo.ai.qna.service;

import com.nexilo.ai.qna.entity.DocumentChunk;
import com.nexilo.ai.qna.repository.DocumentChunkRepository;
import com.nexilo.ai.qna.utils.TextChunker;
import com.nexilo.ai.summary.entity.SummaryDocument;
import com.nexilo.ai.summary.repository.SummaryDocumentRepository;
import com.nexilo.common.exception.ErrorCode;
import com.nexilo.common.exception.NexiloException;
import com.nexilo.storage.FileStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Service d'ingestion de documents dans le vector store.
 *
 * <p>Pipeline :
 * <ol>
 *   <li>Chargement du document depuis {@code summary_documents}</li>
 *   <li>Récupération du texte extrait (stocké lors du résumé) ou ré-extraction</li>
 *   <li>Découpe en chunks via {@link TextChunker} (~800 tokens, overlap 100 tokens)</li>
 *   <li>Génération des embeddings par batch de 10 via {@link EmbeddingModel}</li>
 *   <li>Sauvegarde des chunks + embeddings dans {@code document_chunks}</li>
 *   <li>Marquage du document comme ingéré ({@code ingested=true})</li>
 * </ol>
 *
 * <p><b>EmbeddingModel</b> : fourni par OpenAI ({@code text-embedding-3-small}).
 * Configurer {@code OPENAI_API_KEY} dans les variables d'environnement.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentIngestionService {

    private static final int EMBEDDING_BATCH_SIZE = 10;

    private final SummaryDocumentRepository documentRepository;
    private final DocumentChunkRepository chunkRepository;
    private final TextChunker textChunker;
    private final EmbeddingModel embeddingModel;
    private final JdbcTemplate jdbcTemplate;
    private final FileStorageService fileStorageService;
    private final com.nexilo.processing.utils.PdfExtractor pdfExtractor;

    // =========================================================================
    // Public API
    // =========================================================================

    /**
     * Ingère un document dans le vector store.
     *
     * <p>Si le document est déjà ingéré ({@code ingested=true}),
     * re-ingère depuis zéro (supprime les anciens chunks).
     *
     * @param documentId UUID du document à ingérer
     * @return nombre de chunks créés
     */
    @Transactional
    public int ingestDocument(UUID documentId) {
        // 1. Charger le document
        SummaryDocument doc = documentRepository.findById(documentId)
                .orElseThrow(() -> new NexiloException(
                        ErrorCode.DOCUMENT_NOT_FOUND, HttpStatus.NOT_FOUND,
                        "Document introuvable : " + documentId));

        log.info("Ingestion démarrée — documentId={}, fichier='{}'",
                documentId, doc.getFileName());

        // 2. Nettoyer les anciens chunks si ré-ingestion
        if (doc.isIngested()) {
            log.info("Re-ingestion : suppression des {} chunks existants",
                    chunkRepository.countByDocumentId(documentId));
            chunkRepository.deleteByDocumentId(documentId);
        }

        // 3. Récupérer ou ré-extraire le texte
        String text = resolveText(doc);

        // 4. Découper en chunks
        List<String> chunks = textChunker.chunk(text);
        if (chunks.isEmpty()) {
            throw new NexiloException(ErrorCode.PROCESSING_ERROR, HttpStatus.UNPROCESSABLE_ENTITY,
                    "Aucun chunk généré — le document est peut-être vide ou illisible");
        }
        log.info("Découpage : {} chunks générés pour documentId={}", chunks.size(), documentId);

        // 5. Générer les embeddings par batch et sauvegarder
        int saved = processChunksInBatches(doc, chunks);

        // 6. Marquer le document comme ingéré
        doc.setIngested(true);
        doc.setIngestedAt(Instant.now());
        documentRepository.save(doc);

        log.info("Ingestion terminée — documentId={}, {} chunks sauvegardés", documentId, saved);
        return saved;
    }

    /**
     * Retourne la liste des documents non encore ingérés.
     */
    public List<UUID> findPendingDocuments() {
        return documentRepository.findAll().stream()
                .filter(d -> !d.isIngested())
                .map(SummaryDocument::getId)
                .toList();
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    /**
     * Récupère le texte extrait stocké en base, ou relance l'extraction PDF.
     * L'extraction en mémoire est limitée — pour les gros volumes, un stockage
     * fichier (S3) sera ajouté à l'étape 6.
     */
    /**
     * Récupère le texte extrait stocké en base, ou re-extrait depuis le fichier PDF en stockage.
     */
    private String resolveText(SummaryDocument doc) {
        String stored = doc.getExtractedText();
        if (stored != null && !stored.isBlank()) {
            log.debug("Texte récupéré depuis la base ({} chars)", stored.length());
            return stored;
        }
        // Fallback : re-extraction depuis le stockage de fichiers
        if (doc.getStorageKey() != null) {
            log.info("Texte absent en base — re-extraction depuis le stockage (key={})", doc.getStorageKey());
                try (var stream = fileStorageService.retrieve(doc.getStorageKey())) {
                    String text = pdfExtractor.extract(stream);
                if (text != null && !text.isBlank()) {
                    // Mettre en cache pour les prochains accès
                    doc.setExtractedText(text);
                    documentRepository.save(doc);
                    return text;
                }
            } catch (Exception e) {
                log.warn("Impossible de re-extraire le texte depuis le stockage : {}", e.getMessage());
            }
        }
        throw new NexiloException(ErrorCode.PROCESSING_ERROR, HttpStatus.UNPROCESSABLE_ENTITY,
                "Le texte extrait n'est pas disponible pour le document " + doc.getId()
                        + ". Ré-uploadez le PDF via POST /api/v1/documents/summarize.");
    }

    /**
     * Traite les chunks par batches : génère les embeddings, crée les entités
     * DocumentChunk via JPA, puis met à jour les embeddings via JDBC.
     */
    private int processChunksInBatches(SummaryDocument doc, List<String> chunks) {
        int totalSaved = 0;

        for (int batchStart = 0; batchStart < chunks.size(); batchStart += EMBEDDING_BATCH_SIZE) {
            int batchEnd = Math.min(batchStart + EMBEDDING_BATCH_SIZE, chunks.size());
            List<String> batch = chunks.subList(batchStart, batchEnd);

            log.debug("Batch {}/{} — génération de {} embeddings",
                    (batchStart / EMBEDDING_BATCH_SIZE) + 1,
                    (int) Math.ceil((double) chunks.size() / EMBEDDING_BATCH_SIZE),
                    batch.size());

            // Générer les embeddings avec retry
            List<float[]> embeddings = generateEmbeddings(batch);

            // Sauvegarder chunk par chunk
            for (int j = 0; j < batch.size(); j++) {
                int chunkIndex = batchStart + j;
                DocumentChunk chunk = chunkRepository.save(DocumentChunk.builder()
                        .document(doc)
                        .content(batch.get(j))
                        .chunkIndex(chunkIndex)
                        .metadata("{\"batch\": " + (batchStart / EMBEDDING_BATCH_SIZE) + "}")
                        .build());

                // Mise à jour de l'embedding via JDBC (type pgvector non géré par Hibernate)
                updateEmbedding(chunk.getId(), embeddings.get(j));
                totalSaved++;
            }
        }
        return totalSaved;
    }

    /**
     * Génère les embeddings pour un batch de textes.
     * Retryable : 3 tentatives avec backoff exponentiel (2s → 4s → 8s).
     */
    @Retryable(
            retryFor = NexiloException.class,
            maxAttempts = 3,
            backoff = @Backoff(delay = 2000, multiplier = 2.0, maxDelay = 10_000)
    )
    public List<float[]> generateEmbeddings(List<String> texts) {
        try {
            List<float[]> result = new ArrayList<>();
            for (String text : texts) {
                float[] embedding = embeddingModel.embed(text);
                result.add(embedding);
            }
            return result;
        } catch (Exception e) {
            log.error("Erreur génération embedding : {}", e.getMessage());
            throw new NexiloException(ErrorCode.AI_SERVICE_ERROR, HttpStatus.SERVICE_UNAVAILABLE,
                    "Erreur lors de la génération des embeddings : " + e.getMessage(), e);
        }
    }

    /**
     * Met à jour la colonne {@code embedding} via JDBC.
     * Le tableau {@code float[]} est converti au format pgvector : {@code [x1,x2,...]}.
     *
     * @param chunkId   UUID du chunk
     * @param embedding vecteur d'embedding
     */
    private void updateEmbedding(UUID chunkId, float[] embedding) {
        String vectorLiteral = toVectorLiteral(embedding);
        jdbcTemplate.update(
                "UPDATE document_chunks SET embedding = ?::vector WHERE id = ?::uuid",
                vectorLiteral, chunkId.toString()
        );
    }

    /**
     * Convertit un tableau {@code float[]} en littéral pgvector : {@code [0.1,0.2,...]}.
     */
    private static String toVectorLiteral(float[] embedding) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < embedding.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(embedding[i]);
        }
        sb.append(']');
        return sb.toString();
    }
}

