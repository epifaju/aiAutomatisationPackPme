package com.aipack.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aipack.ai.AiParsingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class DocumentExtractionParserTest {

    private final DocumentExtractionParser parser = new DocumentExtractionParser(new ObjectMapper());

    @Test
    void parsesInvoiceJson() {
        DocumentExtractionParser.ParsedExtraction result = parser.parse(
                """
                {
                  "documentType": "facture",
                  "supplier": "SARL Dupont",
                  "invoiceNumber": "F-12",
                  "invoiceDate": "2026-01-15",
                  "dueDate": "2026-02-15",
                  "amountExcludingTax": 100,
                  "vat": 20,
                  "amountIncludingTax": 120,
                  "currency": "eur",
                  "summary": "Facture de prestation.",
                  "confidenceScore": 0.91
                }
                """);
        assertThat(result.documentType()).isEqualTo("FACTURE");
        assertThat(result.confidenceScore()).isEqualByComparingTo(new BigDecimal("0.91"));
        assertThat(result.json().get("invoiceNumber")).isEqualTo("F-12");
        assertThat(result.json().get("invoiceDate")).isEqualTo("2026-01-15");
        assertThat(result.json().get("currency")).isEqualTo("EUR");
    }

    @Test
    void extractsFromMarkdownFence() {
        DocumentExtractionParser.ParsedExtraction result = parser.parse(
                """
                ```json
                {"documentType":"DEVIS","summary":"Devis de maintenance.","confidenceScore":0.8}
                ```
                """);
        assertThat(result.documentType()).isEqualTo("DEVIS");
        assertThat(result.json().get("currency")).isEqualTo("EUR");
    }

    @Test
    void unknownTypeFallsBackToAutre() {
        DocumentExtractionParser.ParsedExtraction result = parser.parse(
                """
                {"documentType":"TICKET","summary":"Attestation diverse.","confidenceScore":0.5}
                """);
        assertThat(result.documentType()).isEqualTo("AUTRE");
    }

    @Test
    void acceptsFrenchDates() {
        DocumentExtractionParser.ParsedExtraction result = parser.parse(
                """
                {"documentType":"FACTURE","summary":"x","invoiceDate":"15/01/2026","dueDate":"31-08-2026","confidenceScore":0.5}
                """);
        assertThat(result.json().get("invoiceDate")).isEqualTo("2026-01-15");
        assertThat(result.json().get("dueDate")).isEqualTo("2026-08-31");
    }

    @Test
    void missingSummaryGetsDefault() {
        DocumentExtractionParser.ParsedExtraction result = parser.parse(
                """
                {"documentType":"FACTURE","confidenceScore":0.5}
                """);
        assertThat(result.json().get("summary")).asString().isNotBlank();
    }

    @Test
    void acceptsStringAmountsAndPercentConfidence() {
        DocumentExtractionParser.ParsedExtraction result = parser.parse(
                """
                {"documentType":"FACTURE","summary":"ok","amountIncludingTax":"120,50","confidenceScore":80,"currency":"euro"}
                """);
        assertThat(result.json().get("amountIncludingTax")).isEqualTo(new BigDecimal("120.50"));
        assertThat(result.confidenceScore()).isEqualByComparingTo(new BigDecimal("0.8000"));
        assertThat(result.json().get("currency")).isEqualTo("EUR");
    }

    @Test
    void rejectsNonJson() {
        assertThatThrownBy(() -> parser.parse("pas de json ici"))
                .isInstanceOf(AiParsingException.class);
    }
}
