package com.nexilo.ai.qna.repository;

import com.nexilo.ai.qna.entity.QnaMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface QnaMessageRepository extends JpaRepository<QnaMessage, UUID> {
    List<QnaMessage> findBySessionIdOrderByCreatedAtAsc(UUID sessionId);
    long countBySessionId(UUID sessionId);
}

