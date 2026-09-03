package com.aipack.document;

import java.util.Locale;

public enum DocumentType {
    FACTURE,
    DEVIS,
    BON_COMMANDE,
    CONTRAT,
    COURRIER,
    AUTRE;

    public static DocumentType from(String value) {
        try {
            return DocumentType.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (RuntimeException ex) {
            throw DocumentException.invalidType();
        }
    }
}
