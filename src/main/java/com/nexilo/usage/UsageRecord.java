package com.nexilo.usage;

import com.nexilo.user.entity.FeatureType;
import com.nexilo.user.entity.UserPlan;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

/**
 * Enregistrement d'un usage IA pour analytics et facturation future.
 *
 * <p>Les coûts sont stockés en <em>micro-dollars</em> (Long) pour éviter
 * les imprécisions float. 1 USD = 1 000 000 micro-dollars.
 *
 * <p>Barème claude-sonnet-4-5 :
 * <ul>
 *   <li>Input  : $3  / million tokens = 3 micro-dollars / token</li>
 *   <li>Output : $15 / million tokens = 15 micro-dollars / token</li>
 *   <li>Taux mixte estimé (~30% output) : 6 micro-dollars / token</li>
 * </ul>
 */
@Entity
@Table(name = "usage_records")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UsageRecord {

    // --- Coûts en micro-dollars par token (claude-sonnet-4-5) ---
    public static final long MICRO_USD_PER_INPUT_TOKEN  = 3L;   // $3 / M tokens
    public static final long MICRO_USD_PER_OUTPUT_TOKEN = 15L;  // $15 / M tokens
    /** Taux mixte estimé quand on ne distingue pas input/output (~70% in / 30% out). */
    public static final long MICRO_USD_PER_TOKEN_BLENDED = 6L;

    @Id
    @UuidGenerator
    @Column(updatable = false, nullable = false)
    private UUID id;

    /** Identifiant de l'utilisateur ayant déclenché l'opération. */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** Type de fonctionnalité IA utilisée. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private FeatureType feature;

    /** Nombre total de tokens consommés (estimation si non fourni par l'API). */
    @Column(name = "tokens_used", nullable = false)
    @Builder.Default
    private int tokensUsed = 0;

    /**
     * Coût estimé en micro-dollars.
     * Calcul : {@code tokensUsed * MICRO_USD_PER_TOKEN_BLENDED}
     */
    @Column(name = "cost_micro_usd", nullable = false)
    @Builder.Default
    private long costMicroUsd = 0L;

    /** Document concerné par l'opération (nullable). */
    @Column(name = "document_id")
    private UUID documentId;

    /** Durée de l'appel IA en millisecondes. */
    @Column(name = "response_time_ms")
    private Long responseTimeMs;

    // --- Champs spécifiques aux conversions ---

    /** Format d'entrée pour les conversions (ex: "PDF", "DOCX"). */
    @Column(name = "input_format", length = 20)
    private String inputFormat;

    /** Format de sortie pour les conversions (ex: "DOCX", "XLSX", "PNG"). */
    @Column(name = "output_format", length = 20)
    private String outputFormat;

    /** Taille du fichier source en octets (conversions). */
    @Column(name = "input_size_bytes")
    private Long inputSizeBytes;

    /** Taille du fichier résultat en octets (conversions). */
    @Column(name = "output_size_bytes")
    private Long outputSizeBytes;

    /** Plan de l'utilisateur au moment de l'appel (snapshot pour facturation). */
    @Enumerated(EnumType.STRING)
    @Column(name = "plan_at_time", nullable = false, length = 20)
    @Builder.Default
    private UserPlan planAtTime = UserPlan.FREE;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    // =========================================================================
    // Fabriques utilitaires
    // =========================================================================

    /**
     * Calcule le coût en micro-dollars à partir du nombre de tokens.
     * Utilise le taux mixte (blended) : 6 micro-dollars par token.
     */
    public static long calculateCost(int tokens) {
        return (long) tokens * MICRO_USD_PER_TOKEN_BLENDED;
    }

    /**
     * Calcule le coût en micro-dollars en distinguant input et output.
     */
    public static long calculateCost(int inputTokens, int outputTokens) {
        return (long) inputTokens * MICRO_USD_PER_INPUT_TOKEN
                + (long) outputTokens * MICRO_USD_PER_OUTPUT_TOKEN;
    }

    /**
     * Convertit des micro-dollars en dollars USD (pour affichage).
     */
    public double getCostUsd() {
        return costMicroUsd / 1_000_000.0;
    }
}

