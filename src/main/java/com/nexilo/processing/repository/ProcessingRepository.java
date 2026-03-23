package com.nexilo.processing.repository;

import com.nexilo.processing.entity.ProcessingJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProcessingRepository extends JpaRepository<ProcessingJob, Long> {
    List<ProcessingJob> findByStatus(ProcessingJob.JobStatus status);
}

