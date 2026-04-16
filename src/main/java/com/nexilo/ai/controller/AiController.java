package com.nexilo.ai.controller;

import com.nexilo.ai.dto.AiRequestDto;
import com.nexilo.ai.dto.AiResponseDto;
import com.nexilo.ai.service.AiHistoryService;
import com.nexilo.ai.service.AiProviderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Controleur REST pour les fonctionnalites IA :
 * - historique des requetes
 * - endpoint de test de connectivite
 */
@RestController
@RequestMapping("/api/v1/ai")
@RequiredArgsConstructor
@Tag(name = "AI", description = "AI interaction APIs")
public class AiController {

    private final AiProviderService aiProviderService;
    private final AiHistoryService aiHistoryService;

    /**
     * Envoie une requete au provider IA et la persiste dans l'historique.
     */
    @PostMapping("/process")
    @Operation(summary = "Process a new AI request and save to history")
    public ResponseEntity<AiResponseDto> processRequest(@Valid @RequestBody AiRequestDto request) {
        return ResponseEntity.ok(aiHistoryService.save(request));
    }

    /**
     * Retourne l'historique de toutes les requetes IA.
     */
    @GetMapping
    @Operation(summary = "Get all AI request history")
    public ResponseEntity<List<AiResponseDto>> getAllRequests() {
        return ResponseEntity.ok(aiHistoryService.findAll());
    }

    /**
     * Retourne une requete IA par son identifiant.
     */
    @GetMapping("/{id}")
    @Operation(summary = "Get AI request by ID")
    public ResponseEntity<AiResponseDto> getRequest(@PathVariable Long id) {
        return ResponseEntity.ok(aiHistoryService.findById(id));
    }

    /**
     * Endpoint de diagnostic : teste la connectivité avec Gemini.
     */
    @GetMapping("/test")
    @Operation(summary = "Test Gemini connectivity")
    public ResponseEntity<String> testGemini() {
        String result = aiProviderService.summarize(
                "This is a short test document to verify the AI integration is working correctly.");
        return ResponseEntity.ok(result);
    }

    /**
     * Endpoint de diagnostic avancé : affiche le modèle utilisé et le résultat brut.
     */
    @GetMapping("/diagnose")
    @Operation(summary = "Full Gemini diagnostic with model info")
    public ResponseEntity<java.util.Map<String, String>> diagnose() {
        String result = aiProviderService.summarize("Test: respond with OK");
        return ResponseEntity.ok(java.util.Map.of(
                "result", result,
                "status", result.startsWith("AI summary failed") ? "ERROR" : "OK"
        ));
    }
}
