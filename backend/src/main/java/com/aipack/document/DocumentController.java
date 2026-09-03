package com.aipack.document;

import com.aipack.auth.AuthUser;
import com.aipack.common.api.ApiResponse;
import com.aipack.common.api.PageResponse;
import com.aipack.document.dto.ApproveDocumentRequest;
import com.aipack.document.dto.DocumentResponse;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/documents")
public class DocumentController {

    private final DocumentService documentService;

    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    @GetMapping
    public ApiResponse<PageResponse<DocumentResponse>> list(
            @AuthenticationPrincipal AuthUser principal,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String documentType,
            @RequestParam(required = false) String q,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ApiResponse.ok(PageResponse.from(
                documentService.list(principal.companyId(), status, documentType, q, pageable)));
    }

    @GetMapping("/{id}")
    public ApiResponse<DocumentResponse> get(@AuthenticationPrincipal AuthUser principal, @PathVariable UUID id) {
        return ApiResponse.ok(documentService.get(principal.companyId(), id));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<DocumentResponse>> upload(
            @AuthenticationPrincipal AuthUser principal,
            @RequestPart("file") MultipartFile file,
            @RequestParam(required = false) String documentType,
            @RequestParam(required = false, defaultValue = "true") boolean process) {
        DocumentService.IngestResult result = documentService.upload(principal.companyId(), file, documentType, process);
        HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(ApiResponse.ok(result.document()));
    }

    @PostMapping("/{id}/process")
    public ApiResponse<DocumentResponse> process(@AuthenticationPrincipal AuthUser principal, @PathVariable UUID id) {
        return ApiResponse.ok(documentService.process(principal.companyId(), id));
    }

    @PostMapping("/{id}/approve")
    public ApiResponse<DocumentResponse> approve(
            @AuthenticationPrincipal AuthUser principal,
            @PathVariable UUID id,
            @RequestBody(required = false) ApproveDocumentRequest request) {
        return ApiResponse.ok(documentService.approve(principal.companyId(), id, request));
    }

    @PostMapping("/{id}/reject")
    public ApiResponse<DocumentResponse> reject(@AuthenticationPrincipal AuthUser principal, @PathVariable UUID id) {
        return ApiResponse.ok(documentService.reject(principal.companyId(), id));
    }
}
