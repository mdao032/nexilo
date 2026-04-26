package com.nexilo.user.quota;

import com.nexilo.common.exception.ErrorCode;
import com.nexilo.common.exception.NexiloException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * API de consultation des quotas de l'utilisateur courant.
 *
 * <p>Endpoint : {@code GET /api/v1/quota/me}
 */
@RestController
@RequestMapping("/api/v1/quota")
@RequiredArgsConstructor
@Tag(name = "Quota", description = "Consultation des quotas et du plan de l'utilisateur")
public class QuotaController {

    private final QuotaService quotaService;

    /**
     * Retourne l'état des quotas de l'utilisateur authentifié.
     *
     * @param userDetails utilisateur courant (injecté par Spring Security)
     * @return quotas restants par feature + config du plan
     */
    @GetMapping("/me")
    @Operation(
            summary = "Mes quotas",
            description = "Retourne les compteurs journaliers et les limites de votre plan actif."
    )
    public ResponseEntity<QuotaStatusResponse> getMyQuota(
            @AuthenticationPrincipal UserDetails userDetails) {
        Long userId = resolveUserId(userDetails);
        return ResponseEntity.ok(quotaService.getRemainingQuota(userId));
    }

    private Long resolveUserId(UserDetails userDetails) {
        if (userDetails == null) {
            throw new NexiloException(ErrorCode.INVALID_CREDENTIALS, HttpStatus.UNAUTHORIZED,
                    "Utilisateur non authentifié");
        }
        return (long) Math.abs(userDetails.getUsername().hashCode());
    }
}

