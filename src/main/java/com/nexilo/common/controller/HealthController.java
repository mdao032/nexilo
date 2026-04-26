package com.nexilo.common.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

/**
 * Endpoint de santé public — utilisé pour vérifier que l'API est opérationnelle.
 * Accessible sans JWT.
 */
@RestController
@RequestMapping("/api/v1/health")
@Tag(name = "Health", description = "Vérification de l'état de l'application")
public class HealthController {

    @Value("${spring.application.name:nexilo}")
    private String appName;

    /**
     * Retourne l'état de l'application.
     * Endpoint public — aucun JWT requis.
     */
    @GetMapping
    @Operation(summary = "Health check", description = "Retourne l'état et la version de l'API")
    @SecurityRequirements // Retire l'obligation JWT pour cet endpoint dans Swagger
    public Map<String, String> health() {
        return Map.of(
                "status", "UP",
                "version", "0.0.1",
                "application", appName,
                "timestamp", Instant.now().toString()
        );
    }
}

