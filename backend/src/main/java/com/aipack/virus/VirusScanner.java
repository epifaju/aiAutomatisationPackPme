package com.aipack.virus;

/**
 * Scan antivirus avant stockage (documents + pièces jointes email).
 */
public interface VirusScanner {

    /**
     * Lève {@link MalwareDetectedException} si infecté, ou {@link VirusScanUnavailableException}
     * si le scanner est requis mais indisponible ({@code fail-open=false}).
     */
    void assertClean(byte[] content, String filename);
}
