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
    void rejectsUnknownType() {
        assertThatThrownBy(() -> parser.parse(
                        """
                        {"documentType":"TICKET","summary":"x","confidenceScore":0.5}
                        """))
                .isInstanceOf(AiParsingException.class);
    }

    @Test
    void rejectsInvalidDate() {
        assertThatThrownBy(() -> parser.parse(
                        """
                        {"documentType":"FACTURE","summary":"x","invoiceDate":"15/01/2026","confidenceScore":0.5}
                        """))
                .isInstanceOf(AiParsingException.class);
    }

    @Test
    void rejectsMissingSummary() {
        assertThatThrownBy(() -> parser.parse(
                        """
                        {"documentType":"FACTURE","confidenceScore":0.5}
                        """))
                .isInstanceOf(AiParsingException.class);
    }
}
