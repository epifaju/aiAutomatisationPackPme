package com.aipack.invoice.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record OverdueDetectionResponse(
        int invoicesMarkedOverdue, int remindersCreated, List<InvoiceResponse> invoices) {}
