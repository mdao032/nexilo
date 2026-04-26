package com.nexilo.ai.qna;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexilo.ai.qna.controller.QnaController;
import com.nexilo.ai.qna.dto.QnaRequest;
import com.nexilo.ai.qna.dto.QnaResponse;
import com.nexilo.ai.qna.service.QnaService;
import com.nexilo.common.exception.ErrorCode;
import com.nexilo.common.exception.NexiloException;
import com.nexilo.common.security.JwtService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Tests d'intégration du {@link QnaController}.
 *
 * <p>Utilise {@code @WebMvcTest} pour tester la couche HTTP + Spring Security + validation
 * sans démarrer un serveur complet ni une base de données.
 * Le {@link QnaService} est mocké.
 *
 * <p>Pour un test d'intégration complet avec base de données réelle,
 * utiliser {@code @SpringBootTest} avec Testcontainers (PostgreSQL + pgvector).
 */
@WebMvcTest(QnaController.class)
@DisplayName("QnaController — Tests d'intégration HTTP")
class QnaControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private QnaService qnaService;

    // Beans requis par Spring Security
    @MockitoBean
    private JwtService jwtService;


    @MockitoBean
    private UserDetailsService userDetailsService;

    private static final UUID DOCUMENT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID SESSION_ID  = UUID.fromString("22222222-2222-2222-2222-222222222222");

    // =========================================================================
    // POST /api/v1/documents/{documentId}/ask
    // =========================================================================

    @Test
    @WithMockUser(username = "user@nexilo.com")
    @DisplayName("askQuestion — réponse 200 avec question valide")
    void askQuestion_validRequest_returns200() throws Exception {
        QnaRequest request = new QnaRequest();
        request.setQuestion("Quels sont les points clés du document ?");

        QnaResponse response = QnaResponse.builder()
                .sessionId(SESSION_ID)
                .answer("Les points clés sont : 1. Innovation 2. Croissance")
                .sources(List.of("Le document présente une stratégie de croissance..."))
                .confidence(0.87)
                .answeredAt(Instant.now())
                .build();

        when(qnaService.askQuestion(eq(DOCUMENT_ID), any(), any())).thenReturn(response);

        mockMvc.perform(post("/api/v1/documents/{id}/ask", DOCUMENT_ID)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").value(SESSION_ID.toString()))
                .andExpect(jsonPath("$.answer").isNotEmpty())
                .andExpect(jsonPath("$.confidence").value(0.87));
    }

    @Test
    @WithMockUser(username = "user@nexilo.com")
    @DisplayName("askQuestion — 400 si question vide")
    void askQuestion_emptyQuestion_returns400() throws Exception {
        QnaRequest request = new QnaRequest();
        request.setQuestion("");

        mockMvc.perform(post("/api/v1/documents/{id}/ask", DOCUMENT_ID)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
    }

    @Test
    @WithMockUser(username = "user@nexilo.com")
    @DisplayName("askQuestion — 400 si question > 500 caractères")
    void askQuestion_questionTooLong_returns400() throws Exception {
        QnaRequest request = new QnaRequest();
        request.setQuestion("A".repeat(501));

        mockMvc.perform(post("/api/v1/documents/{id}/ask", DOCUMENT_ID)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
    }

    @Test
    @WithMockUser(username = "user@nexilo.com")
    @DisplayName("askQuestion — 404 si document introuvable")
    void askQuestion_documentNotFound_returns404() throws Exception {
        QnaRequest request = new QnaRequest();
        request.setQuestion("Question valide");

        when(qnaService.askQuestion(any(), any(), any()))
                .thenThrow(new NexiloException(ErrorCode.DOCUMENT_NOT_FOUND,
                        HttpStatus.NOT_FOUND, "Document introuvable"));

        mockMvc.perform(post("/api/v1/documents/{id}/ask", DOCUMENT_ID)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("DOCUMENT_NOT_FOUND"));
    }

    @Test
    @WithMockUser(username = "user@nexilo.com")
    @DisplayName("askQuestion — 403 si document appartient à un autre utilisateur")
    void askQuestion_notOwner_returns403() throws Exception {
        QnaRequest request = new QnaRequest();
        request.setQuestion("Question valide");

        when(qnaService.askQuestion(any(), any(), any()))
                .thenThrow(new NexiloException(ErrorCode.ACCESS_DENIED,
                        HttpStatus.FORBIDDEN, "Vous n'avez pas accès à ce document"));

        mockMvc.perform(post("/api/v1/documents/{id}/ask", DOCUMENT_ID)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));
    }

    @Test
    @DisplayName("askQuestion — 401 si non authentifié")
    void askQuestion_unauthenticated_returns401() throws Exception {
        QnaRequest request = new QnaRequest();
        request.setQuestion("Question valide");

        mockMvc.perform(post("/api/v1/documents/{id}/ask", DOCUMENT_ID)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    // =========================================================================
    // GET /api/v1/documents/{documentId}/sessions/{sessionId}/history
    // =========================================================================

    @Test
    @WithMockUser(username = "user@nexilo.com")
    @DisplayName("getHistory — 200 avec session valide")
    void getHistory_validSession_returns200() throws Exception {
        com.nexilo.ai.qna.dto.QnaHistoryResponse history =
                com.nexilo.ai.qna.dto.QnaHistoryResponse.builder()
                        .sessionId(SESSION_ID)
                        .documentId(DOCUMENT_ID)
                        .createdAt(Instant.now())
                        .lastActivityAt(Instant.now())
                        .messages(List.of())
                        .build();

        when(qnaService.getHistory(eq(DOCUMENT_ID), eq(SESSION_ID), any())).thenReturn(history);

        mockMvc.perform(get("/api/v1/documents/{docId}/sessions/{sessId}/history",
                        DOCUMENT_ID, SESSION_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").value(SESSION_ID.toString()))
                .andExpect(jsonPath("$.messages").isArray());
    }

    @Test
    @WithMockUser(username = "user@nexilo.com")
    @DisplayName("getHistory — 404 si session introuvable")
    void getHistory_sessionNotFound_returns404() throws Exception {
        when(qnaService.getHistory(any(), any(), any()))
                .thenThrow(new NexiloException(ErrorCode.RESOURCE_NOT_FOUND,
                        HttpStatus.NOT_FOUND, "Session introuvable"));

        mockMvc.perform(get("/api/v1/documents/{docId}/sessions/{sessId}/history",
                        DOCUMENT_ID, SESSION_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }
}

