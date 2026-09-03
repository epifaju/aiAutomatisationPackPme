package com.aipack.email;

import com.aipack.common.api.ApiResponse;
import com.aipack.config.WebhookAuthenticator;
import com.aipack.email.dto.EmailResponse;
import com.aipack.email.dto.WebhookEmailRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class EmailWebhookController {

    static final String SECRET_HEADER = "X-Webhook-Secret";

    private final EmailService emailService;
    private final WebhookAuthenticator webhookAuthenticator;

    public EmailWebhookController(EmailService emailService, WebhookAuthenticator webhookAuthenticator) {
        this.emailService = emailService;
        this.webhookAuthenticator = webhookAuthenticator;
    }

    @PostMapping("/webhook/email/incoming")
    public ResponseEntity<ApiResponse<EmailResponse>> incoming(
            @RequestHeader(value = SECRET_HEADER, required = false) String providedSecret,
            @Valid @RequestBody WebhookEmailRequest request) {
        webhookAuthenticator.requireValidSecret(providedSecret);
        boolean analyze = request.analyze() == null || request.analyze();
        EmailService.IngestResult result = emailService.ingestFromWebhook(request, analyze);
        HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(ApiResponse.ok(result.email()));
    }
}
