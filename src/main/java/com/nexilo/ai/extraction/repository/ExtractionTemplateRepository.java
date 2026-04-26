package com.nexilo.ai.extraction.repository;
import com.nexilo.ai.extraction.entity.ExtractionTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;
public interface ExtractionTemplateRepository extends JpaRepository<ExtractionTemplate, UUID> {
    Optional<ExtractionTemplate> findByName(String name);
}