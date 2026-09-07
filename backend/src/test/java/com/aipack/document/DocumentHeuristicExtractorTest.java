package com.aipack.document;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class DocumentHeuristicExtractorTest {

    private final DocumentHeuristicExtractor extractor = new DocumentHeuristicExtractor();

    @Test
    void extractsInvoiceFieldsFromPlainText() {
        String text =
                """
                FACTURE N° E2E-12345
                Fournisseur: Fournisseur E2E SAS
                Date: 2026-08-01
                Échéance: 2026-08-31
                HT: 100.00 EUR
                TVA: 20.00 EUR
                TTC: 120.00 EUR
                """;
        DocumentExtractionParser.ParsedExtraction result = extractor.tryExtract(text, "FACTURE");
        assertThat(result).isNotNull();
        assertThat(result.documentType()).isEqualTo("FACTURE");
        assertThat(result.json().get("supplier")).asString().contains("Fournisseur E2E");
        assertThat(result.json().get("invoiceDate")).isEqualTo("2026-08-01");
        assertThat(result.json().get("dueDate")).isEqualTo("2026-08-31");
        assertThat(result.json().get("amountExcludingTax")).isEqualTo(new BigDecimal("100.00"));
        assertThat(result.json().get("amountIncludingTax")).isEqualTo(new BigDecimal("120.00"));
        assertThat(result.confidenceScore()).isEqualByComparingTo(new BigDecimal("0.45"));
    }

    @Test
    void returnsNullWhenNoUsefulSignal() {
        assertThat(extractor.tryExtract("bonjour sans structure", "AUTRE")).isNull();
    }
}
