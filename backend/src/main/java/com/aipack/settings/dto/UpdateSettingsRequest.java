package com.aipack.settings.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;

public record UpdateSettingsRequest(
        @Valid CompanyPatch company,
        @Valid EmailPatch email,
        @Valid AiPatch ai,
        @Valid InvoicePatch invoices,
        @Valid ReportPatch reports,
        @Valid LeadPatch leads,
        @Valid DocumentPatch documents,
        @Valid SecurityPatch security) {

    public record CompanyPatch(
            @Size(max = 255) String name, @Size(max = 14) String siret, @Size(min = 2, max = 2) String country, String timezone) {}

    public record EmailPatch(Boolean autoSendCompany, BigDecimal confidenceThreshold) {}

    public record AiPatch(
            @Size(max = 64) String provider, @Size(max = 512) String ollamaBaseUrl, @Size(max = 128) String ollamaModel) {}

    public record InvoicePatch(Boolean autoSendCompany, List<Integer> reminderDays) {}

    public record ReportPatch(Boolean autoSendCompany, @Email @Size(max = 320) String dailyReportEmail) {}

    public record LeadPatch(Integer scoreLowMax, Integer scoreMediumMax, Integer scoreHighMax, BigDecimal confidenceThreshold) {}

    public record DocumentPatch(BigDecimal confidenceThreshold) {}

    public record SecurityPatch(Integer dataRetentionDays) {}
}
