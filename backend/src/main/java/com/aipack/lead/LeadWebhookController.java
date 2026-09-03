package com.aipack.lead;

import com.aipack.common.api.ApiResponse;
import com.aipack.config.WebhookAuthenticator;
import com.aipack.lead.dto.LeadResponse;
import com.aipack.lead.dto.WebhookLeadRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class LeadWebhookController {

    static final String SECRET_HEADER = "X-Webhook-Secret";

    private final LeadService leadService;
    private final WebhookAuthenticator webhookAuthenticator;

    public LeadWebhookController(LeadService leadService, WebhookAuthenticator webhookAuthenticator) {
        this.leadService = leadService;
        this.webhookAuthenticator = webhookAuthenticator;
    }

    @PostMapping("/webhook/leads/create")
    public ResponseEntity<ApiResponse<LeadResponse>> create(
            @RequestHeader(value = SECRET_HEADER, required = false) String providedSecret,
            @Valid @RequestBody WebhookLeadRequest request) {
        webhookAuthenticator.requireValidSecret(providedSecret);
        boolean qualify = request.qualify() == null || request.qualify();
        LeadResponse created = leadService.captureFromWebhook(request, qualify);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(created));
    }
}
