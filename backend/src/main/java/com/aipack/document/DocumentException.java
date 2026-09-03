package com.aipack.document;

import org.springframework.http.HttpStatus;

public class DocumentException extends RuntimeException {

    private final String code;
    private final HttpStatus status;

    public DocumentException(String code, String message, HttpStatus status) {
        super(message);
        this.code = code;
        this.status = status;
    }

    public static DocumentException notFound() {
        return new DocumentException("DOCUMENT_NOT_FOUND", "Document introuvable", HttpStatus.NOT_FOUND);
    }

    public static DocumentException companyNotFound() {
        return new DocumentException("COMPANY_NOT_FOUND", "Entreprise introuvable", HttpStatus.NOT_FOUND);
    }

    public static DocumentException invalidStatus() {
        return new DocumentException("INVALID_STATUS", "Statut de document inconnu", HttpStatus.BAD_REQUEST);
    }

    public static DocumentException invalidType() {
        return new DocumentException("INVALID_TYPE", "Type de document inconnu", HttpStatus.BAD_REQUEST);
    }

    public static DocumentException missingFile() {
        return new DocumentException("MISSING_FILE", "Fichier obligatoire", HttpStatus.BAD_REQUEST);
    }

    public static DocumentException fileTooLarge() {
        return new DocumentException("FILE_TOO_LARGE", "Fichier trop volumineux (max 20 Mo)", HttpStatus.PAYLOAD_TOO_LARGE);
    }

    public static DocumentException unsupportedType() {
        return new DocumentException(
                "UNSUPPORTED_TYPE",
                "Format non supporté. Formats MVP : PDF, PNG, JPEG, TXT",
                HttpStatus.UNSUPPORTED_MEDIA_TYPE);
    }

    public static DocumentException processingNotAllowed() {
        return new DocumentException(
                "PROCESSING_NOT_ALLOWED", "Le document est déjà en cours de traitement", HttpStatus.CONFLICT);
    }

    public static DocumentException reviewNotAllowed() {
        return new DocumentException(
                "REVIEW_NOT_ALLOWED", "Aucune extraction en attente de validation", HttpStatus.CONFLICT);
    }

    public static DocumentException storageFailed() {
        return new DocumentException("STORAGE_ERROR", "Échec de stockage du fichier", HttpStatus.BAD_GATEWAY);
    }

    public static DocumentException extractionFailed() {
        return new DocumentException("EXTRACTION_FAILED", "Échec d'extraction du texte", HttpStatus.UNPROCESSABLE_ENTITY);
    }

    public static DocumentException webhookPayload() {
        return new DocumentException(
                "VALIDATION_ERROR",
                "Fournir documentId ou un fichier (originalFilename + contentBase64)",
                HttpStatus.BAD_REQUEST);
    }

    public String getCode() {
        return code;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
