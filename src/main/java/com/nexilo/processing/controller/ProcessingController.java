package com.nexilo.processing.controller;

import com.nexilo.processing.dto.ProcessingRequest;
import com.nexilo.processing.dto.ProcessingResponse;
import com.nexilo.processing.dto.ProcessingResultResponse;
import com.nexilo.processing.service.ProcessingService;
import com.nexilo.processing.service.ProcessingServiceImpl;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/processing")
@RequiredArgsConstructor
@Tag(name = "Processing", description = "Background processing job management")
public class ProcessingController {

    private final ProcessingService processingService;

    /**
     * Crée et lance un nouveau job de traitement.
     *
     * @param request les données de la requête contenant le fichier et les métadonnées
     * @return la réponse contenant les détails du job créé
     */
    @PostMapping
    @Operation(summary = "Create a new processing job")
    public ResponseEntity<ProcessingResponse> createJob(@Valid @ModelAttribute ProcessingRequest request) {
        return new ResponseEntity<>(processingService.createJob(request), HttpStatus.CREATED);
    }

    /**
     * Récupère les détails d'un job de traitement par son identifiant.
     *
     * @param id l'identifiant du job
     * @return la réponse contenant les détails du job
     */
    @GetMapping("/{id}")
    @Operation(summary = "Get job details by ID")
    public ResponseEntity<ProcessingResponse> getJob(@PathVariable Long id) {
        return ResponseEntity.ok(processingService.getJob(id));
    }

    /**
     * Récupère la liste de tous les jobs de traitement.
     *
     * @return une liste contenant les détails de tous les jobs
     */
    @GetMapping
    @Operation(summary = "Get all processing jobs")
    public ResponseEntity<List<ProcessingResponse>> getAllJobs() {
        return ResponseEntity.ok(processingService.getAllJobs());
    }

    /**
     *
     * @param request
     * @return
     */
    @PostMapping(value = "/process", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ProcessingResponse process(@ModelAttribute ProcessingRequest request) {
        return processingService.createJob(request);
    }

    @GetMapping("/{id}/result")
    public ProcessingResultResponse getResult(@PathVariable Long id) {
        return processingService.getResult(id);
    }
}
