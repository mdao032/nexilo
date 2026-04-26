package com.nexilo.storage;

import io.minio.MinioClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Configuration du client MinIO (profil {@code prod} uniquement).
 *
 * <p>Clés de configuration :
 * <ul>
 *   <li>{@code storage.minio.endpoint}   — URL du serveur MinIO</li>
 *   <li>{@code storage.minio.access-key} — clé d'accès</li>
 *   <li>{@code storage.minio.secret-key} — clé secrète</li>
 * </ul>
 */
@Configuration
@Profile("prod")
public class MinioConfig {

    @Value("${storage.minio.endpoint:http://localhost:9000}")
    private String endpoint;

    @Value("${storage.minio.access-key:nexilo}")
    private String accessKey;

    @Value("${storage.minio.secret-key:nexilo123}")
    private String secretKey;

    @Bean
    public MinioClient minioClient() {
        return MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();
    }
}

