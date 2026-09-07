package com.aipack.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.aipack.config.DocumentProperties;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class TikaTextExtractorTest {

    @Test
    void extractsPlainTextWithoutOcr() {
        TikaTextExtractor extractor = new TikaTextExtractor(disabledOcrProps());
        String text = extractor.extract("Facture n° F-2026-001 — 120 EUR TTC".getBytes(), "facture.txt");
        assertThat(text).contains("F-2026-001");
        assertThat(TikaTextExtractor.forPrompt(text)).contains("F-2026-001");
    }

    @Test
    void truncatesPromptText() {
        String longText = "a".repeat(TikaTextExtractor.PROMPT_TEXT_CHARS + 50);
        assertThat(TikaTextExtractor.forPrompt(longText)).hasSize(TikaTextExtractor.PROMPT_TEXT_CHARS);
    }

    @Test
    void ocrExtractsTextFromPngWhenTesseractAvailable() throws Exception {
        assumeTrue(tesseractAvailable(), "tesseract binary required");
        TikaTextExtractor extractor = new TikaTextExtractor(new DocumentProperties(20_971_520, true, "eng", 40));
        byte[] png = renderPngWithText("INVOICE F-42");
        String text = extractor.extract(png, "scan.png");
        assertThat(text.toUpperCase()).containsAnyOf("INVOICE", "F-42", "F42");
    }

    private static DocumentProperties disabledOcrProps() {
        return new DocumentProperties(20_971_520, false, "fra+eng", 40);
    }

    private static boolean tesseractAvailable() {
        try {
            Process process = new ProcessBuilder("tesseract", "--version")
                    .redirectErrorStream(true)
                    .start();
            boolean finished = process.waitFor(5, TimeUnit.SECONDS);
            return finished && process.exitValue() == 0;
        } catch (Exception ex) {
            return false;
        }
    }

    private static byte[] renderPngWithText(String text) throws Exception {
        BufferedImage image = new BufferedImage(480, 120, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, 480, 120);
        g.setColor(Color.BLACK);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 36));
        g.drawString(text, 24, 72);
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }
}
