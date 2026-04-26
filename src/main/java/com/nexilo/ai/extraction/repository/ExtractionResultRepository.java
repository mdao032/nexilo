package com.nexilo.ai.extraction.repository;
import com.nexilo.ai.extraction.entity.ExtractionResultEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;
public interface ExtractionResultRepository extends JpaRepository<ExtractionResultEntity, UUID> {
    List<ExtractionResultEntity> findByDocumentIdOrderByCreatedAtDesc(UUID documentId);
}