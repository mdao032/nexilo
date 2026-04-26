package com.nexilo.ai.qna.controller;

import com.nexilo.ai.qna.dto.QnaHistoryResponse;
import com.nexilo.ai.qna.dto.QnaRequest;
import com.nexilo.ai.qna.dto.QnaResponse;
import com.nexilo.ai.qna.service.QnaService;
import com.nexilo.common.exception.ErrorCode;
import com.nexilo.common.exception.NexiloException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * API de Q&A conversationnel sur documents PDF.
 *
 * <p>Endpoints :
 * <ul>
 *   <li>POST /api/v1/documents/{documentId}/ask — pose une question</li>
 *   <li>GET  /api/v1/documents/{documentId}/sessions/{sessionId}/history — historique</li>
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/documents")
@RequiredArgsConstructor
@Tag(name = "Q&A", description = "Questions-réponses conversationnelles sur documents PDF (RAG)")
public class QnaController {

    private final QnaService qnaService;

    /**
     * Pose une question sur un document PDF.
     *
     * <p>Validations :
     * <ul>
     *   <li>Question : non vide, max 500 caractères</li>
     *   <li>Document : doit exister et appartenir à l'utilisateur courant</li>
     *   <li>SessionId : optionnel — nouvelle session créée si absent</li>
     * </ul>
     */
    @PostMapping("/{documentId}/ask")
    @Operation(
            summary = "Poser une question sur un document",
            description = "Pipeline RAG : embedding → recherche pgvector → Claude. " +
                    "Le document est auto-ingéré si nécessaire. SessionId généré si absent."
    )
    public ResponseEntity<QnaResponse> ask(
            @PathVariable UUID documentId,
            @Valid @RequestBody QnaRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {

        Long userId = resolveUserId(userDetails);
        log.info("Q&A — documentId={}, user={}, question={} chars",
                documentId, userDetails.getUsername(), request.getQuestion().length());

        return ResponseEntity.ok(qnaService.askQuestion(documentId, request, userId));
    }

    /**
     * Récupère l'historique complet d'une session de conversation.
     */
    @GetMapping("/{documentId}/sessions/{sessionId}/history")
    @Operation(
            summary = "Historique d'une session Q&A",
            description = "Retourne la liste chronologique des messages (questions et réponses) d'une session."
    )
    public ResponseEntity<QnaHistoryResponse> getHistory(
            @PathVariable UUID documentId,
            @PathVariable UUID sessionId,
            @AuthenticationPrincipal UserDetails userDetails) {

        Long userId = resolveUserId(userDetails);
        return ResponseEntity.ok(qnaService.getHistory(documentId, sessionId, userId));
    }

    private Long resolveUserId(UserDetails userDetails) {
        if (userDetails == null) {
            throw new NexiloException(ErrorCode.INVALID_CREDENTIALS,
                    org.springframework.http.HttpStatus.UNAUTHORIZED, "Utilisateur non authentifié");
        }
        return (long) Math.abs(userDetails.getUsername().hashCode());
    }
}

