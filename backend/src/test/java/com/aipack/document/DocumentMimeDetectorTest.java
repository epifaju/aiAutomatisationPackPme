package com.aipack.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class DocumentMimeDetectorTest {

    private final DocumentMimeDetector detector = new DocumentMimeDetector();

    @Test
    void detectsPlainText() {
        DocumentMimeDetector.DetectedFile detected = detector.detect("Facture F-1 montant 120 EUR".getBytes(), "facture.txt");
        assertThat(detected.contentType()).isEqualTo("text/plain");
        assertThat(detected.sanitizedFilename()).isEqualTo("facture.txt");
    }

    @Test
    void sanitizesPathAndUnsafeCharacters() {
        assertThat(DocumentMimeDetector.sanitizeFilename("../../facture 2026.pdf")).isEqualTo("facture_2026.pdf");
    }

    @Test
    void rejectsEmptyFile() {
        assertThatThrownBy(() -> detector.detect(new byte[0], "vide.txt")).isInstanceOf(DocumentException.class);
    }

    @Test
    void rejectsMismatchedExtension() {
        byte[] pngHeader = new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};
        assertThatThrownBy(() -> detector.detect(pngHeader, "facture.txt")).isInstanceOf(DocumentException.class);
    }

    @Test
    void rejectsUnknownExtension() {
        assertThatThrownBy(() -> detector.detect("hello".getBytes(), "malware.exe")).isInstanceOf(DocumentException.class);
    }
}
