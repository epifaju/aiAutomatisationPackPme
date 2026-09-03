package com.aipack.invoice;

import java.util.Locale;

public enum InvoiceStatus {
    DRAFT,
    SENT,
    PAID,
    OVERDUE,
    CANCELLED;

    public static InvoiceStatus from(String value) {
        try {
            return InvoiceStatus.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (RuntimeException ex) {
            throw InvoiceException.invalidStatus();
        }
    }

    public boolean allowsReminder() {
        return this == SENT || this == OVERDUE;
    }
}
