package com.aipack.lead;

import com.aipack.auth.AuthUser;
import com.aipack.common.api.ApiResponse;
import com.aipack.common.api.PageResponse;
import com.aipack.lead.dto.CreateLeadRequest;
import com.aipack.lead.dto.LeadImportResponse;
import com.aipack.lead.dto.LeadResponse;
import com.aipack.lead.dto.UpdateLeadRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/leads")
public class LeadController {

    private final LeadService leadService;

    public LeadController(LeadService leadService) {
        this.leadService = leadService;
    }

    @GetMapping
    public ApiResponse<PageResponse<LeadResponse>> list(
            @AuthenticationPrincipal AuthUser principal,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String source,
            @RequestParam(required = false) String q,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ApiResponse.ok(PageResponse.from(leadService.list(principal.companyId(), status, source, q, pageable)));
    }

    @GetMapping("/{id}")
    public ApiResponse<LeadResponse> get(@AuthenticationPrincipal AuthUser principal, @PathVariable UUID id) {
        return ApiResponse.ok(leadService.get(principal.companyId(), id));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<LeadResponse>> create(
            @AuthenticationPrincipal AuthUser principal, @Valid @RequestBody CreateLeadRequest request) {
        LeadResponse created = leadService.create(principal.companyId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(created));
    }

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<LeadImportResponse> importCsv(
            @AuthenticationPrincipal AuthUser principal, @RequestPart("file") MultipartFile file) {
        return ApiResponse.ok(leadService.importCsv(principal.companyId(), file));
    }

    @PutMapping("/{id}")
    public ApiResponse<LeadResponse> update(
            @AuthenticationPrincipal AuthUser principal,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateLeadRequest request) {
        return ApiResponse.ok(leadService.update(principal.companyId(), id, request));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal AuthUser principal, @PathVariable UUID id) {
        leadService.delete(principal.companyId(), id);
    }

    @PostMapping("/{id}/qualify")
    public ApiResponse<LeadResponse> qualify(@AuthenticationPrincipal AuthUser principal, @PathVariable UUID id) {
        return ApiResponse.ok(leadService.qualify(principal.companyId(), id));
    }
}
