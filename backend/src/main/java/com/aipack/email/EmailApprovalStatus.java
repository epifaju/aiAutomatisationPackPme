package com.aipack.email;

import java.util.Locale;

public enum EmailApprovalStatus {
    PENDING_APPROVAL,
    APPROVED,
    REJECTED,
    EXECUTED;

    public static EmailApprovalStatus from(String value) {
        try {
            return EmailApprovalStatus.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (RuntimeException ex) {
            throw EmailException.invalidApproval();
        }
    }
}
