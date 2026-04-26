package com.nexilo.infra.queue;

import com.nexilo.common.exception.ErrorCode;
import com.nexilo.common.exception.NexiloException;
import com.nexilo.infra.queue.dto.JobStatusResponse;
import com.nexilo.infra.queue.dto.JobSubmitRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/**
 * API de gestion des jobs IA asynchrones.
 *
 * <p>Endpoints :
 * <ul>
 *   <li>POST /api/v1/jobs          — soumet un job async (retourne jobId immédiatement)</li>
 *   <li>GET  /api/v1/jobs/{jobId}  — polling du statut + résultat</li>
 *   <li>GET  /api/v1/jobs/my       — liste paginée des jobs de l'utilisateur courant</li>
 * </ul>
 *
 * <p>Pattern d'utilisation recommandé :
 * <ol>
 *   <li>POST /api/v1/jobs → récupère le {@code jobId}</li>
 *   <li>GET  /api/v1/jobs/{jobId} toutes les 2s jusqu'à status=DONE|FAILED</li>
 *   <li>Lire le champ {@code result} (JSON) si status=DONE</li>
 * </ol>
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/jobs")
@RequiredArgsConstructor
@Tag(name = "Jobs", description = "Gestion des jobs IA asynchrones (polling)")
public class JobController {

    private final AiJobService jobService;
    private final AiJobRepository jobRepository;

    // =========================================================================
    // POST /api/v1/jobs — soumettre un job
    // =========================================================================

    /**
     * Soumet un job IA asynchrone.
     *
     * <p>Retourne immédiatement un {@code jobId}. Le traitement effectif se fait
     * en arrière-plan. Utiliser {@code GET /api/v1/jobs/{jobId}} pour suivre l'avancement.
     *
     * @param request     corps de la requête (type, documentId, payload optionnel)
     * @param userDetails utilisateur authentifié
     * @return jobId + statut initial PENDING
     */
    @PostMapping
    @Operation(
            summary = "Soumettre un job asynchrone",
            description = """
                    Soumet un job IA (INGEST, EXTRACTION, QNA, SUMMARY) pour traitement asynchrone.
                    Retourne immédiatement un jobId. Faites un polling sur GET /api/v1/jobs/{jobId}
                    pour suivre la progression.
                    """
    )
    @ApiResponse(responseCode = "202", description = "Job soumis — traitement en cours",
            content = @Content(schema = @Schema(implementation = Map.class)))
    @ApiResponse(responseCode = "400", description = "Requête invalide")
    @ApiResponse(responseCode = "401", description = "Non authentifié")
    public ResponseEntity<Map<String, Object>> submitJob(
            @Valid @RequestBody JobSubmitRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {

        Long userId = resolveUserId(userDetails);
        UUID jobId = jobService.submitJob(request.type(), request.documentId(), userId, request.payload());

        log.info("Job soumis via API — id={}, type={}, user={}", jobId, request.type(), userDetails.getUsername());

        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(Map.of(
                        "jobId", jobId.toString(),
                        "status", AiJobStatus.PENDING.name(),
                        "message", "Job soumis. Suivez l'avancement via GET /api/v1/jobs/" + jobId
                ));
    }

    // =========================================================================
    // GET /api/v1/jobs/{jobId} — statut d'un job (polling)
    // =========================================================================

    /**
     * Récupère le statut et le résultat d'un job (endpoint de polling).
     *
     * @param jobId       UUID du job
     * @param userDetails utilisateur authentifié
     * @return statut complet avec progression, résultat ou message d'erreur
     */
    @GetMapping("/{jobId}")
    @Operation(
            summary = "Statut d'un job (polling)",
            description = """
                    Retourne le statut courant d'un job. Si status=DONE, le champ `result` contient
                    le résultat JSON. Si status=FAILED, le champ `errorMessage` explique l'échec.
                    Polling recommandé toutes les 2 secondes.
                    """
    )
    @ApiResponse(responseCode = "200", description = "Statut du job",
            content = @Content(schema = @Schema(implementation = JobStatusResponse.class)))
    @ApiResponse(responseCode = "404", description = "Job introuvable")
    public ResponseEntity<JobStatusResponse> getJobStatus(
            @Parameter(description = "UUID du job") @PathVariable UUID jobId,
            @AuthenticationPrincipal UserDetails userDetails) {

        Long userId = resolveUserId(userDetails);
        JobStatusResponse response = jobService.getJobStatus(jobId, userId);
        return ResponseEntity.ok(response);
    }

    // =========================================================================
    // GET /api/v1/jobs/my — liste paginée des jobs de l'utilisateur
    // =========================================================================

    /**
     * Retourne la liste paginée des jobs de l'utilisateur courant.
     *
     * @param page        numéro de page (0-indexed, défaut 0)
     * @param size        taille de la page (défaut 20, max 100)
     * @param userDetails utilisateur authentifié
     * @return page de statuts de jobs
     */
    @GetMapping("/my")
    @Operation(
            summary = "Mes jobs",
            description = "Liste paginée des jobs soumis par l'utilisateur authentifié, triés par date décroissante."
    )
    @ApiResponse(responseCode = "200", description = "Liste des jobs")
    public ResponseEntity<Page<JobStatusResponse>> getMyJobs(
            @Parameter(description = "Numéro de page (0-indexed)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Taille de la page (max 100)") @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal UserDetails userDetails) {

        Long userId = resolveUserId(userDetails);
        Pageable pageable = PageRequest.of(page, Math.min(size, 100));
        Page<JobStatusResponse> result = jobRepository
                .findByUserIdOrderByCreatedAtDesc(userId, pageable)
                .map(JobStatusResponse::from);
        return ResponseEntity.ok(result);
    }

    // =========================================================================
    // Helper
    // =========================================================================

    private Long resolveUserId(UserDetails userDetails) {
        if (userDetails == null) {
            throw new NexiloException(ErrorCode.INVALID_CREDENTIALS, HttpStatus.UNAUTHORIZED,
                    "Utilisateur non authentifié");
        }
        return (long) Math.abs(userDetails.getUsername().hashCode());
    }
}

