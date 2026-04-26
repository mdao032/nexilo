package com.nexilo.core.conversion;

import com.nexilo.storage.FileStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Tâche planifiée de nettoyage des fichiers convertis expirés.
 * Tourne toutes les heures. Supprime les fichiers expirés du stockage.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FileCleanupScheduler {

    private final ConversionRepository conversionRepository;
    private final FileStorageService fileStorageService;

    @Scheduled(fixedDelay = 3_600_000, initialDelay = 60_000)
    @Transactional
    public void cleanupExpiredFiles() {
        List<ConversionRecord> expired = conversionRepository.findExpired(Instant.now());
        if (expired.isEmpty()) { log.debug("Nettoyage — aucun fichier expiré"); return; }

        log.info("Nettoyage — {} fichiers expirés", expired.size());
        AtomicLong freed = new AtomicLong(0);
        int deleted = 0;

        for (ConversionRecord record : expired) {
            try {
                if (record.getFileKey() != null) {
                    fileStorageService.delete(record.getFileKey());
                    freed.addAndGet(record.getOutputSizeBytes() != null ? record.getOutputSizeBytes() : 0L);
                }
                record.setStatus("EXPIRED");
                record.setFileKey(null);
                conversionRepository.save(record);
                deleted++;
            } catch (Exception e) {
                log.warn("Impossible de supprimer {} : {}", record.getId(), e.getMessage());
            }
        }
        log.info("Nettoyage terminé — {} fichiers supprimés, {:.2f} MB libérés",
                deleted, freed.get() / (1024.0 * 1024.0));
    }
}

