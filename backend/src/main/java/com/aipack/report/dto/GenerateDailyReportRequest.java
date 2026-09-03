package com.aipack.report.dto;

import java.time.LocalDate;

public record GenerateDailyReportRequest(LocalDate date, Boolean send) {}
