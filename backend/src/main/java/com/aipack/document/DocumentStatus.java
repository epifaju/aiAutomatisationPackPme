package com.aipack.document;

import java.util.Locale;

public enum DocumentStatus {
    UPLOADED,
    PROCESSING,
    EXTRACTED,
    REVIEW_REQUIRED,
    ERROR;

    public static DocumentStatus from(String value) {
        try {
            return DocumentStatus.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (RuntimeException ex) {
            throw DocumentException.invalidStatus();
        }
    }
}
