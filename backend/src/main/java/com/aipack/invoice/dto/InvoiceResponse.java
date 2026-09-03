package com.aipack.invoice.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record InvoiceResponse(
        UUID id,
        UUID companyId,
        CustomerSummary customer,
        String invoiceNumber,
        LocalDate invoiceDate,
        LocalDate dueDate,
        BigDecimal amountExcludingTax,
        BigDecimal vat,
        BigDecimal amountIncludingTax,
        String currency,
        String status,
        int daysOverdue,
        boolean autoSendEnabled,
        List<InvoiceReminderResponse> reminders,
        Instant createdAt,
        Instant updatedAt) {}
