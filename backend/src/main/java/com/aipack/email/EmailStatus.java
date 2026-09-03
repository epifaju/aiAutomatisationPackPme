package com.aipack.email;

import java.util.Locale;

public enum EmailStatus {
    RECEIVED,
    ANALYZED,
    ERROR;

    public static EmailStatus from(String value) {
        try {
            return EmailStatus.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (RuntimeException ex) {
            throw EmailException.invalidStatus();
        }
    }
}
