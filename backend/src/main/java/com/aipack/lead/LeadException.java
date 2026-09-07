package com.aipack.lead;

import org.springframework.http.HttpStatus;

public class LeadException extends RuntimeException {

    private final String code;
    private final HttpStatus status;

    public LeadException(String code, String message, HttpStatus status) {
        super(message);
        this.code = code;
        this.status = status;
    }

    public static LeadException notFound() {
        return new LeadException("LEAD_NOT_FOUND", "Lead introuvable", HttpStatus.NOT_FOUND);
    }

    public static LeadException companyNotFound() {
        return new LeadException("COMPANY_NOT_FOUND", "Entreprise introuvable", HttpStatus.NOT_FOUND);
    }

    public static LeadException invalidStatus() {
        return new LeadException("INVALID_STATUS", "Statut de lead inconnu", HttpStatus.BAD_REQUEST);
    }

    public static LeadException invalidSource() {
        return new LeadException("INVALID_SOURCE", "Source de lead inconnue", HttpStatus.BAD_REQUEST);
    }

    public static LeadException invalidName() {
        return new LeadException("VALIDATION_ERROR", "Le nom du lead est obligatoire", HttpStatus.BAD_REQUEST);
    }

    public static LeadException emptyCsv() {
        return new LeadException("EMPTY_CSV", "Fichier CSV vide", HttpStatus.BAD_REQUEST);
    }

    public static LeadException missingCsvFile() {
        return new LeadException("MISSING_FILE", "Fichier CSV obligatoire", HttpStatus.BAD_REQUEST);
    }

    public static LeadException invalidCsv(String message) {
        return new LeadException("INVALID_CSV", message, HttpStatus.BAD_REQUEST);
    }

    public static LeadException importTooLarge() {
        return new LeadException(
                "IMPORT_TOO_LARGE",
                "Trop de lignes (maximum " + LeadCsvParser.MAX_ROWS + ")",
                HttpStatus.BAD_REQUEST);
    }

    public String getCode() {
        return code;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
