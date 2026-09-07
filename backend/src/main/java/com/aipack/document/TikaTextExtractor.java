package com.aipack.document;

import com.aipack.config.DocumentProperties;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.ocr.TesseractOCRConfig;
import org.apache.tika.parser.pdf.PDFParserConfig;
import org.apache.tika.sax.BodyContentHandler;
import org.springframework.stereotype.Component;
import org.xml.sax.SAXException;

@Component
public class TikaTextExtractor {

    static final int MAX_TEXT_CHARS = 100_000;
    static final int PROMPT_TEXT_CHARS = 12_000;

    private final DocumentProperties documentProperties;

    public TikaTextExtractor(DocumentProperties documentProperties) {
        this.documentProperties = documentProperties;
    }

    public String extract(byte[] content, String filename) {
        String text = parse(content, filename, false);
        if (ocrEnabled() && text.length() < ocrMinChars()) {
            String ocrText = parse(content, filename, true);
            if (ocrText.length() > text.length()) {
                return ocrText;
            }
        }
        return text;
    }

    private String parse(byte[] content, String filename, boolean forceOcr) {
        AutoDetectParser parser = new AutoDetectParser();
        BodyContentHandler handler = new BodyContentHandler(MAX_TEXT_CHARS);
        Metadata metadata = new Metadata();
        if (filename != null && !filename.isBlank()) {
            metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, filename);
        }
        ParseContext context = buildContext(parser, forceOcr);
        try (InputStream in = new ByteArrayInputStream(content)) {
            parser.parse(in, handler, metadata, context);
            String text = handler.toString();
            return text == null ? "" : text.trim();
        } catch (IOException | TikaException | SAXException ex) {
            throw DocumentException.extractionFailed();
        }
    }

    private ParseContext buildContext(Parser parser, boolean forceOcr) {
        ParseContext context = new ParseContext();
        context.set(Parser.class, parser);

        TesseractOCRConfig ocrConfig = new TesseractOCRConfig();
        if (!ocrEnabled()) {
            ocrConfig.setSkipOcr(true);
        } else {
            ocrConfig.setLanguage(ocrLanguages());
            ocrConfig.setTimeoutSeconds(120);
            ocrConfig.setSkipOcr(false);
        }
        context.set(TesseractOCRConfig.class, ocrConfig);

        PDFParserConfig pdfConfig = new PDFParserConfig();
        if (!ocrEnabled()) {
            pdfConfig.setOcrStrategy(PDFParserConfig.OCR_STRATEGY.NO_OCR);
        } else if (forceOcr) {
            pdfConfig.setOcrStrategy(PDFParserConfig.OCR_STRATEGY.OCR_ONLY);
        } else {
            pdfConfig.setOcrStrategy(PDFParserConfig.OCR_STRATEGY.AUTO);
        }
        context.set(PDFParserConfig.class, pdfConfig);
        return context;
    }

    private boolean ocrEnabled() {
        return documentProperties.ocrEnabled();
    }

    private String ocrLanguages() {
        String languages = documentProperties.ocrLanguages();
        return languages == null || languages.isBlank() ? "fra+eng" : languages.trim();
    }

    private int ocrMinChars() {
        int min = documentProperties.ocrMinChars();
        return min > 0 ? min : 40;
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
