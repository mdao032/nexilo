package com.nexilo.ai.qna.repository;

import com.nexilo.ai.qna.entity.QnaSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface QnaSessionRepository extends JpaRepository<QnaSession, UUID> {
    List<QnaSession> findByDocumentIdAndUserIdOrderByLastActivityAtDesc(UUID documentId, Long userId);
    Optional<QnaSession> findByIdAndUserId(UUID id, Long userId);
}

