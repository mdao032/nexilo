package com.nexilo.ai.qna.dto;

import com.nexilo.ai.qna.entity.QnaMessage;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Historique complet d'une session Q&A. */
@Getter
@Builder
public class QnaHistoryResponse {
    private final UUID sessionId;
    private final UUID documentId;
    private final Instant createdAt;
    private final Instant lastActivityAt;
    private final List<MessageDto> messages;

    @Getter
    @Builder
    public static class MessageDto {
        private final UUID id;
        private final QnaMessage.Role role;
        private final String content;
        private final Instant createdAt;
    }
}

