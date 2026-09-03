package com.aipack.document;

import com.aipack.common.api.ApiResponse;
import com.aipack.config.WebhookAuthenticator;
import com.aipack.document.dto.DocumentResponse;
import com.aipack.document.dto.WebhookDocumentRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DocumentWebhookController {

    static final String SECRET_HEADER = "X-Webhook-Secret";

    private final DocumentService documentService;
    private final WebhookAuthenticator webhookAuthenticator;

    public DocumentWebhookController(DocumentService documentService, WebhookAuthenticator webhookAuthenticator) {
        this.documentService = documentService;
        this.webhookAuthenticator = webhookAuthenticator;
    }

    @PostMapping("/webhook/documents/process")
    public ResponseEntity<ApiResponse<DocumentResponse>> process(
            @RequestHeader(value = SECRET_HEADER, required = false) String providedSecret,
            @Valid @RequestBody WebhookDocumentRequest request) {
        webhookAuthenticator.requireValidSecret(providedSecret);
        boolean process = request.process() == null || request.process();
        DocumentService.IngestResult result = documentService.ingestFromWebhook(request, process);
        HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(ApiResponse.ok(result.document()));
    }
}
