package com.nexilo.storage;

import com.nexilo.common.exception.ErrorCode;
import com.nexilo.common.exception.NexiloException;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

/**
 * Implémentation locale du stockage de fichiers (profil {@code dev}).
 *
 * <p>Les fichiers sont stockés dans {@code ${storage.local.path:./uploads}}.
 * Cette implémentation est uniquement destinée au développement local —
 * en production, utiliser {@link MinioFileStorageService}.
 */
@Slf4j
@Service
@Profile("dev")
public class LocalFileStorageService implements FileStorageService {

    @Value("${storage.local.path:./uploads}")
    private String storagePath;

    private Path root;

    @PostConstruct
    public void init() {
        root = Paths.get(storagePath).toAbsolutePath().normalize();
        try {
            Files.createDirectories(root);
            log.info("Stockage local initialisé : {}", root);
        } catch (Exception e) {
            throw new IllegalStateException("Impossible de créer le répertoire de stockage : " + root, e);
        }
    }

    @Override
    public String store(MultipartFile file, String key) {
        try {
            Path dest = resolveSafe(key);
            Files.createDirectories(dest.getParent());
            Files.copy(file.getInputStream(), dest, StandardCopyOption.REPLACE_EXISTING);
            log.info("Fichier stocké localement : {}", dest);
            return key;
        } catch (Exception e) {
            throw new NexiloException(ErrorCode.PROCESSING_ERROR, HttpStatus.INTERNAL_SERVER_ERROR,
                    "Erreur lors du stockage local du fichier : " + e.getMessage(), e);
        }
    }

    @Override
    public InputStream retrieve(String key) {
        try {
            Path file = resolveSafe(key);
            if (!Files.exists(file)) {
                throw new NexiloException(ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND,
                        "Fichier introuvable : " + key);
            }
            return Files.newInputStream(file);
        } catch (NexiloException e) {
            throw e;
        } catch (Exception e) {
            throw new NexiloException(ErrorCode.PROCESSING_ERROR, HttpStatus.INTERNAL_SERVER_ERROR,
                    "Erreur lors de la lecture du fichier : " + e.getMessage(), e);
        }
    }

    @Override
    public void delete(String key) {
        try {
            Path file = resolveSafe(key);
            boolean deleted = Files.deleteIfExists(file);
            if (deleted) log.info("Fichier supprimé : {}", file);
        } catch (Exception e) {
            log.warn("Impossible de supprimer le fichier local {} : {}", key, e.getMessage());
        }
    }

    /** Résout un chemin sûr (path traversal prevention). */
    private Path resolveSafe(String key) {
        Path resolved = root.resolve(key).normalize();
        if (!resolved.startsWith(root)) {
            throw new NexiloException(ErrorCode.MALFORMED_REQUEST, HttpStatus.BAD_REQUEST,
                    "Clé de stockage invalide (path traversal) : " + key);
        }
        return resolved;
    }
}

