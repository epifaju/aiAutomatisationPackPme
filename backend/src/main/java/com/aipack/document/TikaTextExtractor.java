package com.aipack.document;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import org.apache.tika.Tika;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.springframework.stereotype.Component;

@Component
public class TikaTextExtractor {

    static final int MAX_TEXT_CHARS = 100_000;
    static final int PROMPT_TEXT_CHARS = 12_000;

    private final Tika tika;

    public TikaTextExtractor() {
        this.tika = new Tika();
        this.tika.setMaxStringLength(MAX_TEXT_CHARS);
    }

    public String extract(byte[] content, String filename) {
        try (ByteArrayInputStream in = new ByteArrayInputStream(content)) {
            Metadata metadata = new Metadata();
            if (filename != null && !filename.isBlank()) {
                metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, filename);
            }
            String text = tika.parseToString(in, metadata);
            return text == null ? "" : text.trim();
        } catch (IOException | TikaException ex) {
            throw DocumentException.extractionFailed();
        }
    }

    static String forPrompt(String extractedText) {
        if (extractedText == null || extractedText.isBlank()) {
            return "";
        }
        String trimmed = extractedText.trim();
        if (trimmed.length() <= PROMPT_TEXT_CHARS) {
            return trimmed;
        }
        return trimmed.substring(0, PROMPT_TEXT_CHARS);
    }
}
