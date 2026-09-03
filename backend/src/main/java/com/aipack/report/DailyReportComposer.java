package com.aipack.report;

import com.aipack.report.dto.ReportMetrics;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class DailyReportComposer {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    public String compose(String companyName, ReportDayWindow window, ReportMetrics metrics) {
        String date = window.date().format(DATE);
        return "Rapport quotidien du "
                + date
                + " — "
                + (companyName == null ? "Entreprise" : companyName)
                + "\n\n"
                + "Emails reçus : "
                + metrics.emailsReceived()
                + " (urgents : "
                + metrics.emailsUrgent()
                + ")\n"
                + "Nouveaux prospects : "
                + metrics.newLeads()
                + " (prioritaires : "
                + metrics.priorityLeads()
                + ")\n"
                + "Documents traités : "
                + metrics.documentsProcessed()
                + " (en erreur : "
                + metrics.documentsInError()
                + ")\n"
                + "Factures en retard : "
                + metrics.overdueInvoices()
                + " (montant "
                + formatAmount(metrics.overdueAmount())
                + " "
                + metrics.currency()
                + ")\n"
                + "Relances envoyées : "
                + metrics.remindersSent()
                + "\n"
                + "Automatisations exécutées : "
                + metrics.automationsExecuted()
                + " (erreurs : "
                + metrics.automationErrors()
                + ")\n";
    }

    public Map<String, Object> toMap(ReportMetrics metrics) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("emailsReceived", metrics.emailsReceived());
        map.put("emailsUrgent", metrics.emailsUrgent());
        map.put("newLeads", metrics.newLeads());
        map.put("priorityLeads", metrics.priorityLeads());
        map.put("documentsProcessed", metrics.documentsProcessed());
        map.put("documentsInError", metrics.documentsInError());
        map.put("overdueInvoices", metrics.overdueInvoices());
        map.put("overdueAmount", metrics.overdueAmount());
        map.put("currency", metrics.currency());
        map.put("remindersSent", metrics.remindersSent());
        map.put("automationsExecuted", metrics.automationsExecuted());
        map.put("automationErrors", metrics.automationErrors());
        return map;
    }

    public ReportMetrics fromMap(Map<String, Object> map) {
        if (map == null) {
            return new ReportMetrics(0, 0, 0, 0, 0, 0, 0, BigDecimal.ZERO, "EUR", 0, 0, 0);
        }
        return new ReportMetrics(
                longValue(map.get("emailsReceived")),
                longValue(map.get("emailsUrgent")),
                longValue(map.get("newLeads")),
                longValue(map.get("priorityLeads")),
                longValue(map.get("documentsProcessed")),
                longValue(map.get("documentsInError")),
                longValue(map.get("overdueInvoices")),
                decimalValue(map.get("overdueAmount")),
                map.get("currency") == null ? "EUR" : map.get("currency").toString(),
                longValue(map.get("remindersSent")),
                longValue(map.get("automationsExecuted")),
                longValue(map.get("automationErrors")));
    }

    public Map<String, String> promptVariables(String companyName, ReportDayWindow window, ReportMetrics metrics) {
        Map<String, String> variables = new LinkedHashMap<>();
        variables.put("companyName", companyName == null ? "" : companyName);
        variables.put("reportDate", window.date().format(DATE));
        variables.put("emailsReceived", Long.toString(metrics.emailsReceived()));
        variables.put("emailsUrgent", Long.toString(metrics.emailsUrgent()));
        variables.put("newLeads", Long.toString(metrics.newLeads()));
        variables.put("priorityLeads", Long.toString(metrics.priorityLeads()));
        variables.put("documentsProcessed", Long.toString(metrics.documentsProcessed()));
        variables.put("documentsInError", Long.toString(metrics.documentsInError()));
        variables.put("overdueInvoices", Long.toString(metrics.overdueInvoices()));
        variables.put("overdueAmount", formatAmount(metrics.overdueAmount()));
        variables.put("currency", metrics.currency());
        variables.put("remindersSent", Long.toString(metrics.remindersSent()));
        variables.put("automationErrors", Long.toString(metrics.automationErrors()));
        return variables;
    }

    private static String formatAmount(BigDecimal amount) {
        BigDecimal value = amount == null ? BigDecimal.ZERO : amount.setScale(2, RoundingMode.HALF_UP);
        return value.toPlainString();
    }

    private static long longValue(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null) {
            return 0L;
        }
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException ex) {
            return 0L;
        }
    }

    private static BigDecimal decimalValue(Object value) {
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        if (value == null) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(value.toString());
        } catch (NumberFormatException ex) {
            return BigDecimal.ZERO;
        }
    }
}
