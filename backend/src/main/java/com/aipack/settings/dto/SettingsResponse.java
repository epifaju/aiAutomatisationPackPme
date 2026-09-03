package com.aipack.settings.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record SettingsResponse(
        CompanySettingsView company,
        EmailSettingsView email,
        AiSettingsView ai,
        InvoiceSettingsView invoices,
        ReportSettingsView reports,
        LeadSettingsView leads,
        DocumentSettingsView documents,
        SecuritySettingsView security) {

    public record CompanySettingsView(UUID id, String name, String siret, String country, String timezone) {}

    public record EmailSettingsView(boolean autoSendEnv, boolean autoSendCompany, BigDecimal confidenceThreshold) {}

    public record AiSettingsView(
            String runtimeProvider,
            String runtimeOllamaBaseUrl,
            String runtimeOllamaModel,
            boolean cacheEnabled,
            String provider,
            String ollamaBaseUrl,
            String ollamaModel) {}

    public record InvoiceSettingsView(
            boolean autoSendEnv, boolean autoSendCompany, boolean schedulerEnabled, List<Integer> reminderDays) {}

    public record ReportSettingsView(
            boolean autoSendEnv, boolean autoSendCompany, boolean schedulerEnabled, String dailyReportEmail) {}

    public record LeadSettingsView(int scoreLowMax, int scoreMediumMax, int scoreHighMax, BigDecimal confidenceThreshold) {}

    public record DocumentSettingsView(BigDecimal confidenceThreshold) {}

    public record SecuritySettingsView(int dataRetentionDays) {}
}
