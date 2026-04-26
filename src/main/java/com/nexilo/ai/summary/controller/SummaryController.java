package com.nexilo.ai.summary.controller;

import com.nexilo.ai.summary.dto.SummaryResponse;
import com.nexilo.ai.summary.service.SummaryService;
import com.nexilo.common.exception.ErrorCode;
import com.nexilo.common.exception.NexiloException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

/**
 * API de résumé PDF par intelligence artificielle.
 *
 * <p>Endpoints :
 * <ul>
 *   <li>POST /api/v1/documents/summarize — upload + résumé immédiat</li>
 *   <li>GET  /api/v1/documents/{documentId}/summary — récupère un résumé existant</li>
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/documents")
@RequiredArgsConstructor
@Tag(name = "Summary", description = "Résumé de documents PDF par IA (Claude)")
public class SummaryController {

    private final SummaryService summaryService;

    // =========================================================================
    // POST /api/v1/documents/summarize
    // =========================================================================

    /**
     * Upload un fichier PDF et génère son résumé via Claude.
     *
     * <p>Idempotence : si le même PDF a déjà été traité, le résumé existant
     * est retourné immédiatement (champ {@code cached = true}).
     *
     * @param file       le fichier PDF (max 20 MB, MIME application/pdf)
     * @param userDetails l'utilisateur authentifié (injecté par Spring Security)
     * @return le résumé structuré
     */
    @PostMapping(value = "/summarize", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            summary = "Générer un résumé PDF",
            description = "Upload un PDF (max 20 MB) et retourne un résumé structuré généré par Claude. " +
                    "Idempotent : le même PDF retourne le résumé existant (cached=true)."
    )
    @ApiResponse(responseCode = "200", description = "Résumé généré ou récupéré depuis le cache",
            content = @Content(schema = @Schema(implementation = SummaryResponse.class)))
    @ApiResponse(responseCode = "400", description = "Fichier manquant ou invalide")
    @ApiResponse(responseCode = "413", description = "Fichier trop volumineux (> 20 MB)")
    @ApiResponse(responseCode = "415", description = "Type de fichier non supporté (seul PDF accepté)")
    @ApiResponse(responseCode = "503", description = "Service IA temporairement indisponible")
    public ResponseEntity<SummaryResponse> summarize(
            @Parameter(description = "Fichier PDF à analyser (max 20 MB)")
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal UserDetails userDetails) {

        Long userId = resolveUserId(userDetails);
        log.info("Demande de résumé — fichier='{}', taille={} bytes, user={}",
                file.getOriginalFilename(), file.getSize(), userDetails.getUsername());

        SummaryResponse response = summaryService.summarize(file, userId);

        HttpStatus status = response.isCached() ? HttpStatus.OK : HttpStatus.CREATED;
        return ResponseEntity.status(status).body(response);
    }

    // =========================================================================
    // GET /api/v1/documents/{documentId}/summary
    // =========================================================================

    /**
     * Récupère un résumé existant par l'ID du document.
     *
     * @param documentId UUID du document source
     * @return le résumé existant
     */
    @GetMapping("/{documentId}/summary")
    @Operation(
            summary = "Récupérer un résumé existant",
            description = "Retourne le résumé IA précédemment généré pour un document."
    )
    @ApiResponse(responseCode = "200", description = "Résumé trouvé")
    @ApiResponse(responseCode = "404", description = "Aucun résumé pour ce document")
    public ResponseEntity<SummaryResponse> getSummary(
            @Parameter(description = "UUID du document source")
            @PathVariable UUID documentId) {

        log.debug("Récupération du résumé pour documentId={}", documentId);
        return ResponseEntity.ok(summaryService.getByDocumentId(documentId));
    }

    // =========================================================================
    // Helper
    // =========================================================================

    /**
     * Extrait l'ID utilisateur depuis le principal Spring Security.
     * Dans cette implémentation, l'email sert d'identifiant → on utilise un hash stable.
     * À remplacer par une véritable lookup userId quand UserDetails portera l'ID.
     */
    private Long resolveUserId(UserDetails userDetails) {
        if (userDetails == null) {
            throw new NexiloException(ErrorCode.INVALID_CREDENTIALS, HttpStatus.UNAUTHORIZED,
                    "Utilisateur non authentifié");
        }
        // Utilise le hashCode de l'email comme userId temporaire
        // TODO: récupérer le vrai userId depuis UserRepository
        return (long) Math.abs(userDetails.getUsername().hashCode());
    }
}

