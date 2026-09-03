package com.aipack.report.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.UUID;

public record WebhookDailyReportRequest(@NotNull UUID companyId, LocalDate date, Boolean send) {}
