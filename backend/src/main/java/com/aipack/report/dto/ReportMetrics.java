package com.aipack.report.dto;

import java.math.BigDecimal;

public record ReportMetrics(
        long emailsReceived,
        long emailsUrgent,
        long newLeads,
        long priorityLeads,
        long documentsProcessed,
        long documentsInError,
        long overdueInvoices,
        BigDecimal overdueAmount,
        String currency,
        long remindersSent,
        long automationsExecuted,
        long automationErrors) {}
