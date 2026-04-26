package com.nexilo.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Configuration du pool de threads et du mécanisme de retry.
 */
@Configuration
@EnableAsync
@EnableRetry
public class AsyncConfig {

    /**
     * Pool de threads général pour les tâches asynchrones.
     *
     * @return l'executor configuré avec un pool de 4 threads et une file de 100 tâches
     */
    @Bean(name = "processingExecutor")
    public Executor processingExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("nexilo-async-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();
        return executor;
    }

    /**
     * Pool de threads dédié au traitement des jobs IA asynchrones.
     *
     * <p>Séparé du pool général pour éviter que les jobs IA longs
     * (résumé, extraction, ingestion) ne bloquent les autres tâches async.
     *
     * <ul>
     *   <li>corePoolSize=5   — threads toujours actifs</li>
     *   <li>maxPoolSize=20   — pic de charge</li>
     *   <li>queueCapacity=100 — file d'attente avant rejet</li>
     * </ul>
     */
    @Bean(name = "aiJobExecutor")
    public Executor aiJobExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(20);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("nexilo-job-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(120);
        executor.initialize();
        return executor;
    }
}

