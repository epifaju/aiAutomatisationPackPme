package com.aipack.lead;

import java.util.Locale;

public enum LeadSource {
    WEB_FORM,
    WEBHOOK,
    EMAIL,
    CSV,
    API;

    public static LeadSource from(String value) {
        try {
            return LeadSource.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (RuntimeException ex) {
            throw LeadException.invalidSource();
        }
    }
}
