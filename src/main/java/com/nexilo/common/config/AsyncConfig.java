package com.nexilo.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Configuration du pool de threads utilisé pour les traitements asynchrones (@Async).
 * Dédie un executor nommé "processingExecutor" au pipeline de traitement de documents.
 */
@Configuration
public class AsyncConfig {

    /**
     * Crée et configure le pool de threads pour les tâches asynchrones.
     * Ce bean est utilisé par toutes les méthodes annotées @Async.
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
}

