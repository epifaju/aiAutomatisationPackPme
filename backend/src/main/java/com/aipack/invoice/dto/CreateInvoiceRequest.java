package com.aipack.invoice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record CreateInvoiceRequest(
        @NotNull UUID customerId,
        @NotBlank @Size(max = 64) String invoiceNumber,
        @NotNull LocalDate invoiceDate,
        @NotNull LocalDate dueDate,
        @PositiveOrZero BigDecimal amountExcludingTax,
        @PositiveOrZero BigDecimal vat,
        @PositiveOrZero BigDecimal amountIncludingTax,
        @Size(min = 3, max = 3) String currency,
        String status) {}
