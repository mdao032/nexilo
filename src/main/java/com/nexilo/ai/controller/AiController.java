package com.nexilo.ai.controller;

import com.nexilo.ai.dto.AiRequestDto;
import com.nexilo.ai.dto.AiResponseDto;
import com.nexilo.ai.service.AiService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/ai")
@RequiredArgsConstructor
@Tag(name = "AI", description = "AI interaction APIs")
public class AiController {

    private final AiService aiService;

    @PostMapping("/process")
    @Operation(summary = "Process a new AI request")
    public ResponseEntity<AiResponseDto> processRequest(@Valid @RequestBody AiRequestDto request) {
        return ResponseEntity.ok(aiService.processRequest(request));
    }

    @GetMapping
    @Operation(summary = "Get all AI request history")
    public ResponseEntity<List<AiResponseDto>> getAllRequests() {
        return ResponseEntity.ok(aiService.getAllRequests());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get AI request by ID")
    public ResponseEntity<AiResponseDto> getRequest(@PathVariable Long id) {
        return ResponseEntity.ok(aiService.getRequest(id));
    }

    /**
     * Endpoint de diagnostic : teste la connexion Gemini avec un texte minimal.
     * Retourne directement la réponse brute ou le message d'erreur.
     *
     * @return la réponse générée par Gemini ou le fallback en cas d'échec
     */
    @GetMapping("/test")
    @Operation(summary = "Test Gemini connectivity with a minimal prompt")
    public ResponseEntity<String> testGemini() {
        String result = aiService.summarize(
                "This is a short test document to verify the AI integration is working correctly.");
        return ResponseEntity.ok(result);
    }
}

