package com.aipack.document;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TikaTextExtractorTest {

    private final TikaTextExtractor extractor = new TikaTextExtractor();

    @Test
    void extractsPlainText() {
        String text = extractor.extract("Facture n° F-2026-001 — 120 EUR TTC".getBytes(), "facture.txt");
        assertThat(text).contains("F-2026-001");
        assertThat(TikaTextExtractor.forPrompt(text)).contains("F-2026-001");
    }

    @Test
    void truncatesPromptText() {
        String longText = "a".repeat(TikaTextExtractor.PROMPT_TEXT_CHARS + 50);
        assertThat(TikaTextExtractor.forPrompt(longText)).hasSize(TikaTextExtractor.PROMPT_TEXT_CHARS);
    }
}
