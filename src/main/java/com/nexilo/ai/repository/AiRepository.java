package com.nexilo.ai.repository;

import com.nexilo.ai.entity.AiRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AiRepository extends JpaRepository<AiRequest, Long> {
}

