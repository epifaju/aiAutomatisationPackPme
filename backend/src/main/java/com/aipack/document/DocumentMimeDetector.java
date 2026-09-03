package com.aipack.document;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.apache.tika.Tika;
import org.springframework.stereotype.Component;

@Component
public class DocumentMimeDetector {

    static final Set<String> ALLOWED = Set.of("application/pdf", "image/png", "image/jpeg", "text/plain");

    private static final Map<String, String> EXTENSION_HINTS = Map.of(
            "pdf", "application/pdf",
            "png", "image/png",
            "jpg", "image/jpeg",
            "jpeg", "image/jpeg",
            "txt", "text/plain");

    private final Tika tika = new Tika();

    public DetectedFile detect(byte[] content, String originalFilename) {
        if (content == null || content.length == 0) {
            throw DocumentException.missingFile();
        }
        String detected = normalizeMime(tika.detect(content, originalFilename));
        if (!ALLOWED.contains(detected)) {
            throw DocumentException.unsupportedType();
        }
        String extension = extensionOf(originalFilename);
        String expected = extension == null ? null : EXTENSION_HINTS.get(extension);
        if (expected != null && !expected.equals(detected)) {
            throw DocumentException.unsupportedType();
        }
        if (expected == null && extension != null && !extension.isBlank()) {
            throw DocumentException.unsupportedType();
        }
        return new DetectedFile(detected, sanitizeFilename(originalFilename));
    }

    static String normalizeMime(String mime) {
        if (mime == null || mime.isBlank()) {
            return "application/octet-stream";
        }
        String base = mime.split(";")[0].trim().toLowerCase(Locale.ROOT);
        if ("image/jpg".equals(base)) {
            return "image/jpeg";
        }
        return base;
    }

    static String extensionOf(String filename) {
        if (filename == null) {
            return null;
        }
        String name = filename.replace('\\', '/');
        int slash = name.lastIndexOf('/');
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) {
            return "";
        }
        return name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    static String sanitizeFilename(String original) {
        String name = original == null ? "" : original.replace('\\', '/');
        int slash = name.lastIndexOf('/');
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        name = name.replaceAll("[^a-zA-Z0-9._-]", "_");
        if (name.isBlank() || ".".equals(name) || "..".equals(name)) {
            return "document";
        }
        return name.length() > 200 ? name.substring(0, 200) : name;
    }

    public record DetectedFile(String contentType, String sanitizedFilename) {}
}
