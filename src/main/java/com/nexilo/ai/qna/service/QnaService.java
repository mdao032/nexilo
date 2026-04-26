package com.nexilo.ai.qna.service;

import com.nexilo.ai.qna.dto.QnaHistoryResponse;
import com.nexilo.ai.qna.dto.QnaRequest;
import com.nexilo.ai.qna.dto.QnaResponse;

import java.util.UUID;

public interface QnaService {
    QnaResponse askQuestion(UUID documentId, QnaRequest request, Long userId);
    QnaHistoryResponse getHistory(UUID documentId, UUID sessionId, Long userId);
}

