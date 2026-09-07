package com.aipack.common.api;

import com.aipack.audit.AuditException;
import com.aipack.auth.AuthException;
import com.aipack.config.WebhookUnauthorizedException;
import com.aipack.customer.CustomerException;
import com.aipack.document.DocumentException;
import com.aipack.email.EmailException;
import com.aipack.invoice.InvoiceException;
import com.aipack.lead.LeadException;
import com.aipack.report.ReportException;
import com.aipack.settings.SettingsException;
import com.aipack.virus.MalwareDetectedException;
import com.aipack.virus.VirusScanUnavailableException;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(AuthException.class)
    public ResponseEntity<ApiResponse<Void>> handleAuth(AuthException ex) {
        return ResponseEntity.status(ex.getStatus()).body(ApiResponse.failure(ApiError.of(ex.getCode(), ex.getMessage())));
    }

    @ExceptionHandler(AuditException.class)
    public ResponseEntity<ApiResponse<Void>> handleAudit(AuditException ex) {
        return ResponseEntity.status(ex.getStatus()).body(ApiResponse.failure(ApiError.of(ex.getCode(), ex.getMessage())));
    }

    @ExceptionHandler(LeadException.class)
    public ResponseEntity<ApiResponse<Void>> handleLead(LeadException ex) {
        return ResponseEntity.status(ex.getStatus()).body(ApiResponse.failure(ApiError.of(ex.getCode(), ex.getMessage())));
    }

    @ExceptionHandler(EmailException.class)
    public ResponseEntity<ApiResponse<Void>> handleEmail(EmailException ex) {
        return ResponseEntity.status(ex.getStatus()).body(ApiResponse.failure(ApiError.of(ex.getCode(), ex.getMessage())));
    }

    @ExceptionHandler(DocumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleDocument(DocumentException ex) {
        return ResponseEntity.status(ex.getStatus()).body(ApiResponse.failure(ApiError.of(ex.getCode(), ex.getMessage())));
    }

    @ExceptionHandler(CustomerException.class)
    public ResponseEntity<ApiResponse<Void>> handleCustomer(CustomerException ex) {
        return ResponseEntity.status(ex.getStatus()).body(ApiResponse.failure(ApiError.of(ex.getCode(), ex.getMessage())));
    }

    @ExceptionHandler(InvoiceException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvoice(InvoiceException ex) {
        return ResponseEntity.status(ex.getStatus()).body(ApiResponse.failure(ApiError.of(ex.getCode(), ex.getMessage())));
    }

    @ExceptionHandler(ReportException.class)
    public ResponseEntity<ApiResponse<Void>> handleReport(ReportException ex) {
        return ResponseEntity.status(ex.getStatus()).body(ApiResponse.failure(ApiError.of(ex.getCode(), ex.getMessage())));
    }

    @ExceptionHandler(SettingsException.class)
    public ResponseEntity<ApiResponse<Void>> handleSettings(SettingsException ex) {
        return ResponseEntity.status(ex.getStatus()).body(ApiResponse.failure(ApiError.of(ex.getCode(), ex.getMessage())));
    }

    @ExceptionHandler(MalwareDetectedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMalware(MalwareDetectedException ex) {
        return ResponseEntity.status(ex.getStatus()).body(ApiResponse.failure(ApiError.of(ex.getCode(), ex.getMessage())));
    }

    @ExceptionHandler(VirusScanUnavailableException.class)
    public ResponseEntity<ApiResponse<Void>> handleVirusScanUnavailable(VirusScanUnavailableException ex) {
        return ResponseEntity.status(ex.getStatus()).body(ApiResponse.failure(ApiError.of(ex.getCode(), ex.getMessage())));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleTooLarge(MaxUploadSizeExceededException ex) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(ApiResponse.failure(ApiError.of("FILE_TOO_LARGE", "Fichier trop volumineux (max 20 Mo)")));
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingPart(MissingServletRequestPartException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.failure(ApiError.of("MISSING_FILE", "Fichier obligatoire")));
    }

    @ExceptionHandler(WebhookUnauthorizedException.class)
    public ResponseEntity<ApiResponse<Void>> handleWebhook(WebhookUnauthorizedException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.failure(ApiError.of("UNAUTHORIZED", ex.getMessage())));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, Object> details = ex.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(
                        error -> error.getField(),
                        error -> error.getDefaultMessage() != null ? error.getDefaultMessage() : "invalid",
                        (left, right) -> left));
        ApiError error = new ApiError("VALIDATION_ERROR", "Requête invalide", details);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.failure(error));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(NoResourceFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.failure(ApiError.of("NOT_FOUND", "Ressource introuvable")));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception ex) {
        log.error("Unhandled exception", ex);
        ApiError error = ApiError.of("INTERNAL_ERROR", "Une erreur interne est survenue");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse.failure(error));
    }
}
