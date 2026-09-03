package com.aipack.invoice;

import com.aipack.common.api.ApiResponse;
import com.aipack.config.WebhookAuthenticator;
import com.aipack.invoice.dto.OverdueDetectionResponse;
import com.aipack.invoice.dto.WebhookInvoiceReminderRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class InvoiceWebhookController {

    static final String SECRET_HEADER = "X-Webhook-Secret";

    private final InvoiceService invoiceService;
    private final WebhookAuthenticator webhookAuthenticator;

    public InvoiceWebhookController(InvoiceService invoiceService, WebhookAuthenticator webhookAuthenticator) {
        this.invoiceService = invoiceService;
        this.webhookAuthenticator = webhookAuthenticator;
    }

    @PostMapping("/webhook/invoices/reminder")
    public ResponseEntity<ApiResponse<OverdueDetectionResponse>> reminder(
            @RequestHeader(value = SECRET_HEADER, required = false) String providedSecret,
            @Valid @RequestBody WebhookInvoiceReminderRequest request) {
        webhookAuthenticator.requireValidSecret(providedSecret);
        return ResponseEntity.ok(ApiResponse.ok(invoiceService.detectFromWebhook(request)));
    }
}
