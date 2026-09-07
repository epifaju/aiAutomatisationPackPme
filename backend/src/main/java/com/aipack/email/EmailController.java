package com.aipack.email;

import com.aipack.auth.AuthUser;
import com.aipack.common.api.ApiResponse;
import com.aipack.common.api.PageResponse;
import com.aipack.email.dto.EmailResponse;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/emails")
public class EmailController {

    private final EmailService emailService;

    public EmailController(EmailService emailService) {
        this.emailService = emailService;
    }

    @GetMapping
    public ApiResponse<PageResponse<EmailResponse>> list(
            @AuthenticationPrincipal AuthUser principal,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String priority,
            @RequestParam(required = false) String approvalStatus,
            @RequestParam(required = false) String q,
            @PageableDefault(size = 20, sort = "receivedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ApiResponse.ok(PageResponse.from(emailService.list(
                principal.companyId(), status, category, priority, approvalStatus, q, pageable)));
    }

    @GetMapping("/{id}")
    public ApiResponse<EmailResponse> get(@AuthenticationPrincipal AuthUser principal, @PathVariable UUID id) {
        return ApiResponse.ok(emailService.get(principal.companyId(), id));
    }

    @GetMapping("/{id}/attachments/{attachmentId}/content")
    public ResponseEntity<byte[]> downloadAttachment(
            @AuthenticationPrincipal AuthUser principal,
            @PathVariable UUID id,
            @PathVariable UUID attachmentId) {
        EmailService.AttachmentContent file =
                emailService.loadAttachmentContent(principal.companyId(), id, attachmentId);
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(file.filename(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .contentType(MediaType.parseMediaType(file.contentType()))
                .body(file.bytes());
    }

    @PostMapping("/{id}/analyze")
    public ApiResponse<EmailResponse> analyze(@AuthenticationPrincipal AuthUser principal, @PathVariable UUID id) {
        return ApiResponse.ok(emailService.analyze(principal.companyId(), id));
    }

    @PostMapping("/{id}/approve")
    public ApiResponse<EmailResponse> approve(@AuthenticationPrincipal AuthUser principal, @PathVariable UUID id) {
        return ApiResponse.ok(emailService.approve(principal.companyId(), id));
    }

    @PostMapping("/{id}/reject")
    public ApiResponse<EmailResponse> reject(@AuthenticationPrincipal AuthUser principal, @PathVariable UUID id) {
        return ApiResponse.ok(emailService.reject(principal.companyId(), id));
    }

    @PostMapping("/{id}/send")
    public ApiResponse<EmailResponse> send(@AuthenticationPrincipal AuthUser principal, @PathVariable UUID id) {
        return ApiResponse.ok(emailService.send(principal.companyId(), id));
    }
}
