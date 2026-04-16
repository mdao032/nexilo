package com.nexilo.processing.service;

import com.nexilo.processing.dto.ProcessingRequest;
import com.nexilo.processing.dto.ProcessingResponse;
import com.nexilo.processing.dto.ProcessingResultResponse;

import java.util.List;

public interface ProcessingService {
    
    /**
     * Crée un nouveau job de traitement.
     *
     * @param request la requête de traitement contenant le fichier et les paramètres
     * @return la réponse avec les détails du job créé
     */
    ProcessingResponse createJob(ProcessingRequest request);

    /**
     * Récupère un job de traitement spécifique à partir de son identifiant.
     *
     * @param id l'identifiant unique du job
     * @return la réponse avec les détails du job
     */
    ProcessingResponse getJob(Long id);

    /**
     * Liste tous les jobs de traitement disponibles.
     *
     * @return la liste des réponses détaillant chaque job
     */
    List<ProcessingResponse> getAllJobs();

    /**
     * Met à jour le statut d'un job de traitement.
     *
     * @param id l'identifiant du job
     * @param status le nouveau statut à assigner
     * @return la réponse mise à jour du job
     */
    ProcessingResponse updateJobStatus(Long id, String status);

    /**
     * Récupérer le résultat du traitement
     * @param id
     * @return
     */
    ProcessingResultResponse getResult(Long id);
}
