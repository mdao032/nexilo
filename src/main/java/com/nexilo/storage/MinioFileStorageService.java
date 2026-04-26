package com.nexilo.storage;

import com.nexilo.common.exception.ErrorCode;
import com.nexilo.common.exception.NexiloException;
import io.minio.*;
import io.minio.http.Method;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.concurrent.TimeUnit;

/**
 * Implémentation MinIO du stockage de fichiers (profil {@code prod}).
 *
 * <p>Bucket : {@code nexilo-documents} (créé automatiquement si absent).
 * <br>URLs pré-signées valides 1 heure.
 *
 * <p>Configuration requise dans application.yml :
 * <pre>
 * storage:
 *   minio:
 *     endpoint: http://localhost:9000
 *     access-key: nexilo
 *     secret-key: nexilo123
 *     bucket: nexilo-documents
 * </pre>
 */
@Slf4j
@Service
@Profile("prod")
@RequiredArgsConstructor
public class MinioFileStorageService implements FileStorageService {

    private final MinioClient minioClient;

    @Value("${storage.minio.bucket:nexilo-documents}")
    private String bucket;

    @PostConstruct
    public void init() {
        try {
            boolean exists = minioClient.bucketExists(
                    BucketExistsArgs.builder().bucket(bucket).build());
            if (!exists) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
                log.info("Bucket MinIO créé : {}", bucket);
            } else {
                log.info("Bucket MinIO existant : {}", bucket);
            }
        } catch (Exception e) {
            throw new IllegalStateException("Impossible d'initialiser le bucket MinIO : " + e.getMessage(), e);
        }
    }

    @Override
    public String store(MultipartFile file, String key) {
        try {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(key)
                    .stream(file.getInputStream(), file.getSize(), -1)
                    .contentType(file.getContentType() != null ? file.getContentType() : "application/pdf")
                    .build());
            log.info("Fichier stocké dans MinIO : bucket={}, key={}", bucket, key);
            // Retourne l'URL pré-signée (valide 1h)
            return generatePresignedUrl(key);
        } catch (Exception e) {
            throw new NexiloException(ErrorCode.PROCESSING_ERROR, HttpStatus.INTERNAL_SERVER_ERROR,
                    "Erreur lors du stockage MinIO : " + e.getMessage(), e);
        }
    }

    @Override
    public InputStream retrieve(String key) {
        try {
            return minioClient.getObject(GetObjectArgs.builder()
                    .bucket(bucket)
                    .object(key)
                    .build());
        } catch (Exception e) {
            throw new NexiloException(ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND,
                    "Fichier introuvable dans MinIO : " + key, e);
        }
    }

    @Override
    public void delete(String key) {
        try {
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(bucket)
                    .object(key)
                    .build());
            log.info("Fichier supprimé de MinIO : {}", key);
        } catch (Exception e) {
            log.warn("Impossible de supprimer l'objet MinIO {} : {}", key, e.getMessage());
        }
    }

    /**
     * Génère une URL pré-signée GET valide 1 heure.
     *
     * @param key clé de l'objet MinIO
     * @return URL pré-signée
     */
    public String generatePresignedUrl(String key) {
        try {
            return minioClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .method(Method.GET)
                    .bucket(bucket)
                    .object(key)
                    .expiry(1, TimeUnit.HOURS)
                    .build());
        } catch (Exception e) {
            log.warn("Impossible de générer l'URL pré-signée pour {} : {}", key, e.getMessage());
            return null;
        }
    }
}

