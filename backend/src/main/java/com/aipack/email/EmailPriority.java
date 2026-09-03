package com.aipack.email;

import java.util.Locale;

public enum EmailPriority {
    LOW,
    NORMAL,
    HIGH,
    URGENT;

    public static EmailPriority from(String value) {
        try {
            return EmailPriority.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (RuntimeException ex) {
            throw EmailException.invalidPriority();
        }
    }
}
