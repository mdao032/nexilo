package com.nexilo.core.conversion;

/** Niveau de compression PDF — pilote la résolution des images embarquées. */
public enum CompressionLevel {
    SCREEN(72),    // 72 dpi  — fichiers web légers
    EBOOK(150),    // 150 dpi — qualité lecture écran
    PRINTER(300);  // 300 dpi — qualité impression

    public final int dpi;
    CompressionLevel(int dpi) { this.dpi = dpi; }
}

