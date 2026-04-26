package com.nexilo.common.config;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration des métriques Micrometer.
 *
 * <p>Fournit un {@link MeterRegistry} en fallback si Actuator
 * ne l'a pas encore enregistré (dev sans Prometheus, tests unitaires).
 */
@Configuration
public class MetricsConfig {

    /**
     * Fallback MeterRegistry en mémoire.
     * Utilisé uniquement si aucun autre MeterRegistry n'est configuré
     * (ex: Actuator non actif, tests unitaires).
     */
    @Bean
    @ConditionalOnMissingBean(MeterRegistry.class)
    public MeterRegistry simpleMeterRegistry() {
        return new SimpleMeterRegistry();
    }
}

