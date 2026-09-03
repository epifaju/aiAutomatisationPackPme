package com.aipack.email;

import java.util.Locale;

public enum EmailCategory {
    CLIENT,
    PROSPECT,
    FACTURE,
    FOURNISSEUR,
    SUPPORT,
    ADMINISTRATIF,
    SPAM,
    AUTRE;

    public static EmailCategory from(String value) {
        try {
            return EmailCategory.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (RuntimeException ex) {
            throw EmailException.invalidCategory();
        }
    }
}
