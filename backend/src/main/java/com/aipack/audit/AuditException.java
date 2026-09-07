package com.aipack.audit;

import org.springframework.http.HttpStatus;

public class AuditException extends RuntimeException {

    private final String code;
    private final HttpStatus status;

    public AuditException(String code, String message, HttpStatus status) {
        super(message);
        this.code = code;
        this.status = status;
    }

    public static AuditException invalidStatus() {
        return new AuditException("INVALID_STATUS", "Statut d'audit inconnu", HttpStatus.BAD_REQUEST);
    }

    public static AuditException companyNotFound() {
        return new AuditException("COMPANY_NOT_FOUND", "Entreprise introuvable", HttpStatus.NOT_FOUND);
    }

    public String getCode() {
        return code;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
