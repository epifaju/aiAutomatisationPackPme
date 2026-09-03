package com.aipack.lead;

import java.util.Locale;

public enum LeadStatus {
    NEW,
    QUALIFIED,
    CONTACTED,
    PROPOSAL,
    WON,
    LOST;

    public static LeadStatus from(String value) {
        try {
            return LeadStatus.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (RuntimeException ex) {
            throw LeadException.invalidStatus();
        }
    }
}
