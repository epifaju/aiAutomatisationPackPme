package com.aipack.invoice;

import com.aipack.auth.AuthUser;
import com.aipack.common.api.ApiResponse;
import com.aipack.common.api.PageResponse;
import com.aipack.invoice.dto.CreateInvoiceRequest;
import com.aipack.invoice.dto.InvoiceResponse;
import com.aipack.invoice.dto.OverdueDetectionResponse;
import com.aipack.invoice.dto.UpdateInvoiceRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/invoices")
public class InvoiceController {

    private final InvoiceService invoiceService;

    public InvoiceController(InvoiceService invoiceService) {
        this.invoiceService = invoiceService;
    }

    @GetMapping
    public ApiResponse<PageResponse<InvoiceResponse>> list(
            @AuthenticationPrincipal AuthUser principal,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) UUID customerId,
            @RequestParam(required = false) String q,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ApiResponse.ok(PageResponse.from(
                invoiceService.list(principal.companyId(), status, customerId, q, pageable)));
    }

    @GetMapping("/{id}")
    public ApiResponse<InvoiceResponse> get(@AuthenticationPrincipal AuthUser principal, @PathVariable UUID id) {
        return ApiResponse.ok(invoiceService.get(principal.companyId(), id));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<InvoiceResponse>> create(
            @AuthenticationPrincipal AuthUser principal, @Valid @RequestBody CreateInvoiceRequest request) {
        InvoiceResponse created = invoiceService.create(principal.companyId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(created));
    }

    @PutMapping("/{id}")
    public ApiResponse<InvoiceResponse> update(
            @AuthenticationPrincipal AuthUser principal,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateInvoiceRequest request) {
        return ApiResponse.ok(invoiceService.update(principal.companyId(), id, request));
    }

    @PostMapping("/overdue/detect")
    public ApiResponse<OverdueDetectionResponse> detectOverdue(
            @AuthenticationPrincipal AuthUser principal, @RequestParam(required = false) UUID invoiceId) {
        return ApiResponse.ok(invoiceService.detectOverdue(principal.companyId(), invoiceId));
    }

    @PostMapping("/{id}/reminders/{level}/approve")
    public ApiResponse<InvoiceResponse> approve(
            @AuthenticationPrincipal AuthUser principal, @PathVariable UUID id, @PathVariable int level) {
        return ApiResponse.ok(invoiceService.approveReminder(principal.companyId(), id, level));
    }

    @PostMapping("/{id}/reminders/{level}/reject")
    public ApiResponse<InvoiceResponse> reject(
            @AuthenticationPrincipal AuthUser principal, @PathVariable UUID id, @PathVariable int level) {
        return ApiResponse.ok(invoiceService.rejectReminder(principal.companyId(), id, level));
    }

    @PostMapping("/{id}/reminders/{level}/send")
    public ApiResponse<InvoiceResponse> send(
            @AuthenticationPrincipal AuthUser principal, @PathVariable UUID id, @PathVariable int level) {
        return ApiResponse.ok(invoiceService.sendReminder(principal.companyId(), id, level));
    }
}
