package com.aipack.invoice;

import org.springframework.http.HttpStatus;

public class InvoiceException extends RuntimeException {

    private final String code;
    private final HttpStatus status;

    public InvoiceException(String code, String message, HttpStatus status) {
        super(message);
        this.code = code;
        this.status = status;
    }

    public static InvoiceException notFound() {
        return new InvoiceException("INVOICE_NOT_FOUND", "Facture introuvable", HttpStatus.NOT_FOUND);
    }

    public static InvoiceException companyNotFound() {
        return new InvoiceException("COMPANY_NOT_FOUND", "Entreprise introuvable", HttpStatus.NOT_FOUND);
    }

    public static InvoiceException invalidStatus() {
        return new InvoiceException("INVALID_STATUS", "Statut de facture inconnu", HttpStatus.BAD_REQUEST);
    }

    public static InvoiceException invalidReminderLevel() {
        return new InvoiceException("INVALID_REMINDER_LEVEL", "Niveau de relance inconnu", HttpStatus.BAD_REQUEST);
    }

    public static InvoiceException duplicateNumber() {
        return new InvoiceException("DUPLICATE_INVOICE_NUMBER", "Numéro de facture déjà utilisé", HttpStatus.CONFLICT);
    }

    public static InvoiceException reminderNotAllowed() {
        return new InvoiceException(
                "REMINDER_NOT_ALLOWED",
                "Aucune relance n'est possible pour une facture payée, annulée ou en brouillon",
                HttpStatus.CONFLICT);
    }

    public static InvoiceException reminderNotFound() {
        return new InvoiceException("REMINDER_NOT_FOUND", "Relance introuvable", HttpStatus.NOT_FOUND);
    }

    public static InvoiceException approvalNotAllowed() {
        return new InvoiceException("APPROVAL_NOT_ALLOWED", "Aucune relance en attente de validation", HttpStatus.CONFLICT);
    }

    public static InvoiceException sendNotAllowed() {
        return new InvoiceException(
                "SEND_NOT_ALLOWED", "L'envoi n'est possible qu'après approbation, et jamais si la facture est payée", HttpStatus.CONFLICT);
    }

    public static InvoiceException alreadySent() {
        return new InvoiceException("REMINDER_ALREADY_SENT", "Cette relance a déjà été envoyée", HttpStatus.CONFLICT);
    }

    public static InvoiceException missingCustomerEmail() {
        return new InvoiceException(
                "MISSING_CUSTOMER_EMAIL", "Le client n'a pas d'adresse email pour la relance", HttpStatus.CONFLICT);
    }

    public static InvoiceException mailSendFailed() {
        return new InvoiceException("MAIL_SEND_FAILED", "Échec d'envoi de la relance", HttpStatus.BAD_GATEWAY);
    }

    public String getCode() {
        return code;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
