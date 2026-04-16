package com.nexilo.processing.repository;

import com.nexilo.processing.entity.ProcessingJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProcessingRepository extends JpaRepository<ProcessingJob, Long> {
    
    /**
     * Recherche la liste des jobs de traitement en fonction de leur statut.
     *
     * @param status le statut des jobs à rechercher
     * @return la liste des jobs correspondant au statut fourni
     */
    List<ProcessingJob> findByStatus(ProcessingJob.JobStatus status);
}
