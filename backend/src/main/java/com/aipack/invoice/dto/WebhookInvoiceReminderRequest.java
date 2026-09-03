package com.aipack.invoice.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record WebhookInvoiceReminderRequest(@NotNull UUID companyId, UUID invoiceId, Boolean send) {}
