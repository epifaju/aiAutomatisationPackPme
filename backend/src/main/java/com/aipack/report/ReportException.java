package com.aipack.report;

import org.springframework.http.HttpStatus;

public class ReportException extends RuntimeException {

    private final String code;
    private final HttpStatus status;

    public ReportException(String code, String message, HttpStatus status) {
        super(message);
        this.code = code;
        this.status = status;
    }

    public static ReportException notFound() {
        return new ReportException("REPORT_NOT_FOUND", "Rapport introuvable", HttpStatus.NOT_FOUND);
    }

    public static ReportException companyNotFound() {
        return new ReportException("COMPANY_NOT_FOUND", "Entreprise introuvable", HttpStatus.NOT_FOUND);
    }

    public static ReportException missingRecipient() {
        return new ReportException(
                "MISSING_RECIPIENT", "Aucun destinataire configuré pour le rapport quotidien", HttpStatus.CONFLICT);
    }

    public static ReportException mailSendFailed() {
        return new ReportException("MAIL_SEND_FAILED", "Échec d'envoi du rapport", HttpStatus.BAD_GATEWAY);
    }

    public String getCode() {
        return code;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
