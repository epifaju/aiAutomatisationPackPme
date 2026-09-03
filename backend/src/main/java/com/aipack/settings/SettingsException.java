package com.aipack.settings;

import org.springframework.http.HttpStatus;

public class SettingsException extends RuntimeException {

    private final String code;
    private final HttpStatus status;

    public SettingsException(String code, String message, HttpStatus status) {
        super(message);
        this.code = code;
        this.status = status;
    }

    public static SettingsException companyNotFound() {
        return new SettingsException("COMPANY_NOT_FOUND", "Entreprise introuvable", HttpStatus.NOT_FOUND);
    }

    public static SettingsException invalidTimezone() {
        return new SettingsException("INVALID_TIMEZONE", "Fuseau horaire invalide", HttpStatus.BAD_REQUEST);
    }

    public static SettingsException invalidScores() {
        return new SettingsException(
                "INVALID_SCORE_BANDS", "Les seuils de score doivent respecter 0 ≤ bas < moyen < haut ≤ 100", HttpStatus.BAD_REQUEST);
    }

    public static SettingsException invalidThreshold() {
        return new SettingsException("INVALID_THRESHOLD", "Le seuil de confiance doit être entre 0 et 1", HttpStatus.BAD_REQUEST);
    }

    public static SettingsException invalidRetention() {
        return new SettingsException("INVALID_RETENTION", "La durée de conservation doit être positive", HttpStatus.BAD_REQUEST);
    }

    public static SettingsException unknownAutomation() {
        return new SettingsException("UNKNOWN_AUTOMATION", "Automatisation inconnue", HttpStatus.NOT_FOUND);
    }

    public static SettingsException cannotRun() {
        return new SettingsException(
                "CANNOT_RUN",
                "Cette automatisation se lance depuis sa page métier, pas en masse",
                HttpStatus.CONFLICT);
    }

    public static SettingsException autoSendNotSupported() {
        return new SettingsException(
                "AUTO_SEND_NOT_SUPPORTED",
                "Cette automatisation n'a pas d'envoi automatique à activer",
                HttpStatus.CONFLICT);
    }

    public String getCode() {
        return code;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
