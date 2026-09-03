package com.aipack.report.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record DailyReportResponse(
        UUID id,
        UUID companyId,
        LocalDate reportDate,
        ReportMetrics metrics,
        String summary,
        String status,
        Instant sentAt,
        Instant createdAt,
        Instant updatedAt) {}
