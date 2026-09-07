package com.aipack.audit;

import com.aipack.audit.dto.AuditLogResponse;
import com.aipack.audit.dto.WebhookN8nErrorRequest;
import com.aipack.common.api.ApiResponse;
import com.aipack.config.WebhookAuthenticator;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AuditWebhookController {

    static final String SECRET_HEADER = "X-Webhook-Secret";

    private final AuditService auditService;
    private final WebhookAuthenticator webhookAuthenticator;

    public AuditWebhookController(AuditService auditService, WebhookAuthenticator webhookAuthenticator) {
        this.auditService = auditService;
        this.webhookAuthenticator = webhookAuthenticator;
    }

    @PostMapping("/webhook/audit/n8n-error")
    public ResponseEntity<ApiResponse<AuditLogResponse>> n8nError(
            @RequestHeader(value = SECRET_HEADER, required = false) String providedSecret,
            @Valid @RequestBody WebhookN8nErrorRequest request) {
        webhookAuthenticator.requireValidSecret(providedSecret);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(auditService.recordN8nError(request)));
    }
}
