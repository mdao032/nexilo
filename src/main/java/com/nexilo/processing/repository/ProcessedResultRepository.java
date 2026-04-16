package com.nexilo.processing.repository;

import com.nexilo.document.entity.ProcessedResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ProcessedResultRepository extends JpaRepository<ProcessedResult, Long> {
    Optional<ProcessedResult> findByJobId(Long jobId);
}
