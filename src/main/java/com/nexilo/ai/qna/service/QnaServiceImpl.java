package com.nexilo.ai.qna.service;

import com.nexilo.ai.qna.dto.QnaHistoryResponse;
import com.nexilo.ai.qna.dto.QnaRequest;
import com.nexilo.ai.qna.dto.QnaResponse;
import com.nexilo.ai.qna.entity.QnaMessage;
import com.nexilo.ai.qna.entity.QnaSession;
import com.nexilo.ai.qna.repository.QnaMessageRepository;
import com.nexilo.ai.qna.repository.QnaSessionRepository;
import com.nexilo.ai.summary.entity.SummaryDocument;
import com.nexilo.ai.summary.repository.SummaryDocumentRepository;
import com.nexilo.common.exception.ErrorCode;
import com.nexilo.common.exception.NexiloException;
import com.nexilo.user.entity.FeatureType;
import com.nexilo.user.quota.CheckQuota;
import com.nexilo.usage.UsageRecord;
import com.nexilo.usage.UsageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Implémentation du service Q&A avec pipeline RAG (Retrieval-Augmented Generation).
 *
 * <p>Algorithme :
 * <ol>
 *   <li>Vérification ownership du document</li>
 *   <li>Auto-ingestion si le document n'est pas encore dans le vector store</li>
 *   <li>Embedding de la question via OpenAI</li>
 *   <li>Recherche des 5 chunks les plus proches via pgvector ({@code <=>})</li>
 *   <li>Construction du prompt RAG avec le contexte récupéré</li>
 *   <li>Génération de la réponse via Claude (ChatClient)</li>
 *   <li>Persistance de la question et de la réponse</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QnaServiceImpl implements QnaService {

    private static final int TOP_K = 5;
    private static final String RAG_SYSTEM_PROMPT = """
            Tu es un assistant expert qui répond UNIQUEMENT à partir du contexte fourni.
            Si la réponse ne se trouve pas dans le contexte, dis-le clairement : \
            "Je ne trouve pas cette information dans le document."
            Ne fabrique jamais d'information. Sois précis, concis et cite les passages pertinents.
            Réponds dans la même langue que la question.
            """;

    private final ChatClient chatClient;
    private final EmbeddingModel embeddingModel;
    private final JdbcTemplate jdbcTemplate;
    private final DocumentIngestionService ingestionService;
    private final SummaryDocumentRepository documentRepository;
    private final QnaSessionRepository sessionRepository;
    private final QnaMessageRepository messageRepository;
    private final UsageService usageService;

    // =========================================================================
    // Q&A principal
    // =========================================================================

    @Override
    @Transactional
    @CheckQuota(feature = FeatureType.QNA)
    public QnaResponse askQuestion(UUID documentId, QnaRequest request, Long userId) {

        // 1. Valider le document et l'ownership
        SummaryDocument doc = loadAndVerifyDocument(documentId, userId);

        // 2. Auto-ingestion si nécessaire
        if (!doc.isIngested()) {
            log.info("Document {} non ingéré — ingestion automatique avant Q&A", documentId);
            ingestionService.ingestDocument(documentId);
            // Recharger pour avoir ingested=true
            doc = loadAndVerifyDocument(documentId, userId);
        }

        // 3. Résoudre / créer la session
        QnaSession session = resolveSession(request.getSessionId(), documentId, userId);

        // 4. Embedding de la question
        float[] questionEmbedding = embedQuestion(request.getQuestion());

        // 5. Recherche RAG — top K chunks
        List<ChunkResult> chunks = searchSimilarChunks(documentId, questionEmbedding);
        if (chunks.isEmpty()) {
            throw new NexiloException(ErrorCode.PROCESSING_ERROR, HttpStatus.UNPROCESSABLE_ENTITY,
                    "Aucun contexte trouvé dans le document pour répondre à cette question");
        }

        // 6. Construire le prompt RAG et appeler Claude
        String context = chunks.stream()
                .map(c -> c.content)
                .collect(Collectors.joining("\n\n---\n\n"));
        long t0 = System.currentTimeMillis();
        String answer = callClaude(context, request.getQuestion());
        long responseTimeMs = System.currentTimeMillis() - t0;

        // 7. Calculer la confidence (moyenne des similarités cosinus)
        double confidence = chunks.stream()
                .mapToDouble(c -> c.similarity)
                .average()
                .orElse(0.0);

        // 8. Persister question + réponse
        saveMessages(session.getId(), request.getQuestion(), answer);
        touchSession(session);

        log.info("Q&A — sessionId={}, confiance={:.2f}, {} sources", session.getId(), confidence, chunks.size());

        // 9. Tracking usage (async)
        int tokensEst = (context.length() + request.getQuestion().length() + answer.length()) / 4;
        usageService.recordUsage(UsageRecord.builder()
                .userId(userId)
                .feature(FeatureType.QNA)
                .tokensUsed(tokensEst)
                .costMicroUsd(UsageRecord.calculateCost(tokensEst))
                .documentId(documentId)
                .responseTimeMs(responseTimeMs)
                .build());

        return QnaResponse.builder()
                .sessionId(session.getId())
                .answer(answer)
                .sources(chunks.stream().map(c -> c.content).toList())
                .confidence(Math.round(confidence * 100.0) / 100.0)
                .answeredAt(Instant.now())
                .build();
    }

    // =========================================================================
    // Historique
    // =========================================================================

    @Override
    @Transactional(readOnly = true)
    public QnaHistoryResponse getHistory(UUID documentId, UUID sessionId, Long userId) {
        QnaSession session = sessionRepository.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new NexiloException(ErrorCode.RESOURCE_NOT_FOUND,
                        HttpStatus.NOT_FOUND, "Session introuvable : " + sessionId));

        if (!session.getDocumentId().equals(documentId)) {
            throw new NexiloException(ErrorCode.ACCESS_DENIED, HttpStatus.FORBIDDEN,
                    "Cette session n'appartient pas à ce document");
        }

        List<QnaHistoryResponse.MessageDto> messages = messageRepository
                .findBySessionIdOrderByCreatedAtAsc(sessionId)
                .stream()
                .map(m -> QnaHistoryResponse.MessageDto.builder()
                        .id(m.getId())
                        .role(m.getRole())
                        .content(m.getContent())
                        .createdAt(m.getCreatedAt())
                        .build())
                .toList();

        return QnaHistoryResponse.builder()
                .sessionId(session.getId())
                .documentId(session.getDocumentId())
                .createdAt(session.getCreatedAt())
                .lastActivityAt(session.getLastActivityAt())
                .messages(messages)
                .build();
    }

    // =========================================================================
    // Helpers privés
    // =========================================================================

    private SummaryDocument loadAndVerifyDocument(UUID documentId, Long userId) {
        SummaryDocument doc = documentRepository.findById(documentId)
                .orElseThrow(() -> new NexiloException(ErrorCode.DOCUMENT_NOT_FOUND,
                        HttpStatus.NOT_FOUND, "Document introuvable : " + documentId));
        if (!doc.getUserId().equals(userId)) {
            throw new NexiloException(ErrorCode.ACCESS_DENIED, HttpStatus.FORBIDDEN,
                    "Vous n'avez pas accès à ce document");
        }
        return doc;
    }

    private QnaSession resolveSession(UUID requestedSessionId, UUID documentId, Long userId) {
        if (requestedSessionId != null) {
            return sessionRepository.findByIdAndUserId(requestedSessionId, userId)
                    .orElseGet(() -> createSession(documentId, userId));
        }
        return createSession(documentId, userId);
    }

    private QnaSession createSession(UUID documentId, Long userId) {
        return sessionRepository.save(QnaSession.builder()
                .documentId(documentId)
                .userId(userId)
                .build());
    }

    private float[] embedQuestion(String question) {
        try {
            return embeddingModel.embed(question);
        } catch (Exception e) {
            throw new NexiloException(ErrorCode.AI_SERVICE_ERROR, HttpStatus.SERVICE_UNAVAILABLE,
                    "Erreur lors de l'embedding de la question : " + e.getMessage(), e);
        }
    }

    /** Recherche pgvector via <=> (distance cosinus). Retourne contenu + score de similarité. */
    private List<ChunkResult> searchSimilarChunks(UUID documentId, float[] queryEmbedding) {
        String vectorLiteral = toVectorLiteral(queryEmbedding);
        String sql = """
                SELECT content, 1 - (embedding <=> ?::vector) AS similarity
                FROM document_chunks
                WHERE document_id = ?::uuid
                  AND embedding IS NOT NULL
                ORDER BY embedding <=> ?::vector
                LIMIT ?
                """;
        return jdbcTemplate.query(sql,
                (rs, rowNum) -> new ChunkResult(rs.getString("content"), rs.getDouble("similarity")),
                vectorLiteral, documentId.toString(), vectorLiteral, TOP_K);
    }

    private String callClaude(String context, String question) {
        try {
            String result = chatClient.prompt()
                    .system(RAG_SYSTEM_PROMPT)
                    .user(u -> u.text("""
                            Contexte du document :
                            {context}
                            
                            Question : {question}
                            """)
                            .param("context", context)
                            .param("question", question))
                    .call()
                    .content();
            return result != null ? result.trim() : "Je ne peux pas répondre à cette question.";
        } catch (Exception e) {
            throw new NexiloException(ErrorCode.AI_SERVICE_ERROR, HttpStatus.SERVICE_UNAVAILABLE,
                    "Le service IA est temporairement indisponible : " + e.getMessage(), e);
        }
    }

    private void saveMessages(UUID sessionId, String question, String answer) {
        messageRepository.save(QnaMessage.builder()
                .sessionId(sessionId).role(QnaMessage.Role.USER).content(question).build());
        messageRepository.save(QnaMessage.builder()
                .sessionId(sessionId).role(QnaMessage.Role.ASSISTANT).content(answer).build());
    }

    private void touchSession(QnaSession session) {
        session.setLastActivityAt(Instant.now());
        sessionRepository.save(session);
    }

    private static String toVectorLiteral(float[] v) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < v.length; i++) { if (i > 0) sb.append(','); sb.append(v[i]); }
        return sb.append(']').toString();
    }

    /** Résultat d'une recherche de chunk : contenu + score de similarité cosinus. */
    private record ChunkResult(String content, double similarity) {}
}

