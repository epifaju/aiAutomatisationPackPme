package com.aipack.invoice.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record InvoiceReminderResponse(
        UUID id,
        int reminderLevel,
        String status,
        Instant scheduledAt,
        Instant sentAt,
        Instant createdAt,
        Instant updatedAt) {}
