package com.aipack.email;

import org.springframework.http.HttpStatus;

public class EmailException extends RuntimeException {

    private final String code;
    private final HttpStatus status;

    public EmailException(String code, String message, HttpStatus status) {
        super(message);
        this.code = code;
        this.status = status;
    }

    public static EmailException notFound() {
        return new EmailException("EMAIL_NOT_FOUND", "Email introuvable", HttpStatus.NOT_FOUND);
    }

    public static EmailException companyNotFound() {
        return new EmailException("COMPANY_NOT_FOUND", "Entreprise introuvable", HttpStatus.NOT_FOUND);
    }

    public static EmailException invalidStatus() {
        return new EmailException("INVALID_STATUS", "Statut d'email inconnu", HttpStatus.BAD_REQUEST);
    }

    public static EmailException invalidCategory() {
        return new EmailException("INVALID_CATEGORY", "Catégorie d'email inconnue", HttpStatus.BAD_REQUEST);
    }

    public static EmailException invalidPriority() {
        return new EmailException("INVALID_PRIORITY", "Priorité d'email inconnue", HttpStatus.BAD_REQUEST);
    }

    public static EmailException invalidApproval() {
        return new EmailException("INVALID_APPROVAL", "Statut d'approbation inconnu", HttpStatus.BAD_REQUEST);
    }

    public static EmailException alreadySent() {
        return new EmailException("EMAIL_ALREADY_SENT", "La réponse a déjà été envoyée", HttpStatus.CONFLICT);
    }

    public static EmailException sendNotAllowed() {
        return new EmailException(
                "SEND_NOT_ALLOWED", "L'envoi n'est possible qu'après approbation d'une réponse suggérée", HttpStatus.CONFLICT);
    }

    public static EmailException approvalNotAllowed() {
        return new EmailException(
                "APPROVAL_NOT_ALLOWED", "Aucune réponse en attente de validation", HttpStatus.CONFLICT);
    }

    public static EmailException mailSendFailed() {
        return new EmailException("MAIL_SEND_FAILED", "Échec d'envoi de l'email", HttpStatus.BAD_GATEWAY);
    }

    public String getCode() {
        return code;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
