package com.aipack.report.dto;

import com.aipack.audit.dto.AuditLogResponse;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDate;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record DashboardSummaryResponse(LocalDate date, ReportMetrics metrics, List<AuditLogResponse> recentActivity) {}
