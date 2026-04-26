package com.nexilo.core.conversion;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;

/**
 * Configuration et détection de LibreOffice.
 *
 * <p>Si LibreOffice n'est pas installé au démarrage, les conversions
 * Word/Excel/PPT → PDF sont désactivées (fallback : message d'erreur clair).
 * Les autres conversions (PDF → Word, merge, compress…) ne sont pas affectées.
 */
@Slf4j
@Component
@Getter
public class LibreOfficeConfig {

    @Value("${LIBREOFFICE_PATH:/usr/bin/libreoffice}")
    private String libreOfficePath;

    private boolean available = false;

    @PostConstruct
    public void detect() {
        File lo = new File(libreOfficePath);
        if (lo.exists() && lo.canExecute()) {
            available = true;
            log.info("LibreOffice détecté : {} — conversions Office→PDF activées", libreOfficePath);
        } else {
            // Tentative sur les chemins alternatifs courants
            String[] alternatives = {
                "/usr/bin/libreoffice",
                "/usr/bin/soffice",
                "/usr/lib/libreoffice/program/soffice",
                "C:\\Program Files\\LibreOffice\\program\\soffice.exe",
                "C:\\Program Files (x86)\\LibreOffice\\program\\soffice.exe"
            };
            for (String alt : alternatives) {
                File f = new File(alt);
                if (f.exists() && f.canExecute()) {
                    libreOfficePath = alt;
                    available = true;
                    log.info("LibreOffice trouvé (chemin alternatif) : {}", alt);
                    return;
                }
            }
            log.warn("LibreOffice introuvable (chemin configuré : {}). " +
                     "Les conversions Word/Excel/PPT → PDF seront désactivées. " +
                     "Installer LibreOffice ou configurer LIBREOFFICE_PATH.", libreOfficePath);
        }
    }
}

