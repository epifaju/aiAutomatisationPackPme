package com.aipack.report;

import static org.assertj.core.api.Assertions.assertThat;

import com.aipack.report.dto.ReportMetrics;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DailyReportComposerTest {

    private final DailyReportComposer composer = new DailyReportComposer();

    @Test
    void composeIncludesKeyMetrics() {
        ReportDayWindow window = ReportDayWindow.of(LocalDate.of(2026, 9, 2), "Europe/Paris");
        String text = composer.compose("Demo SAS", window, metrics());
        assertThat(text).contains("02/09/2026");
        assertThat(text).contains("Demo SAS");
        assertThat(text).contains("Emails reçus : 4 (urgents : 1)");
        assertThat(text).contains("Factures en retard : 2 (montant 240.50 EUR)");
        assertThat(text).contains("Relances envoyées : 3");
    }

    @Test
    void fromMapAcceptsHibernateJsonNumbers() {
        ReportMetrics restored = composer.fromMap(Map.ofEntries(
                Map.entry("emailsReceived", 4),
                Map.entry("emailsUrgent", 1L),
                Map.entry("newLeads", 2),
                Map.entry("priorityLeads", 1),
                Map.entry("documentsProcessed", 3),
                Map.entry("documentsInError", 0),
                Map.entry("overdueInvoices", 2),
                Map.entry("overdueAmount", 240.5),
                Map.entry("currency", "EUR"),
                Map.entry("remindersSent", 3),
                Map.entry("automationsExecuted", 10),
                Map.entry("automationErrors", 1)));
        assertThat(restored.emailsReceived()).isEqualTo(4);
        assertThat(restored.overdueAmount()).isEqualByComparingTo("240.5");
        assertThat(restored.automationErrors()).isEqualTo(1);
        assertThat(composer.fromMap(null).currency()).isEqualTo("EUR");
    }

    private static ReportMetrics metrics() {
        return new ReportMetrics(4, 1, 2, 1, 3, 0, 2, new BigDecimal("240.50"), "EUR", 3, 10, 1);
    }
}
