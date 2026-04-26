package com.nexilo.storage;

import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;

/**
 * Service d'abstraction du stockage de fichiers PDF.
 *
 * <p>Deux implémentations :
 * <ul>
 *   <li>{@link LocalFileStorageService} — profil {@code dev} : disque local</li>
 *   <li>{@link MinioFileStorageService}  — profil {@code prod} : MinIO S3-compatible</li>
 * </ul>
 */
public interface FileStorageService {

    /**
     * Stocke un fichier sous la clé donnée.
     *
     * @param file contenu du fichier à stocker
     * @param key  chemin/clé de stockage (ex: "42/uuid/fichier.pdf")
     * @return URL ou chemin d'accès au fichier stocké
     */
    String store(MultipartFile file, String key);

    /**
     * Récupère un fichier par sa clé.
     *
     * @param key clé de stockage
     * @return flux de lecture du fichier
     */
    InputStream retrieve(String key);

    /**
     * Supprime un fichier par sa clé.
     *
     * @param key clé de stockage
     */
    void delete(String key);

    /**
     * Génère la clé de stockage pour un fichier.
     * Format : {@code {userId}/{documentId}/{fileName}}
     */
    static String buildKey(Long userId, String documentId, String fileName) {
        // Sanitize le nom de fichier
        String safe = fileName != null
                ? fileName.replaceAll("[^a-zA-Z0-9._-]", "_")
                : "document.pdf";
        return userId + "/" + documentId + "/" + safe;
    }
}

