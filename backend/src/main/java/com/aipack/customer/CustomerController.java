package com.aipack.customer;

import com.aipack.auth.AuthUser;
import com.aipack.common.api.ApiResponse;
import com.aipack.common.api.PageResponse;
import com.aipack.customer.dto.CreateCustomerRequest;
import com.aipack.customer.dto.CustomerResponse;
import com.aipack.customer.dto.UpdateCustomerRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/customers")
public class CustomerController {

    private final CustomerService customerService;

    public CustomerController(CustomerService customerService) {
        this.customerService = customerService;
    }

    @GetMapping
    public ApiResponse<PageResponse<CustomerResponse>> list(
            @AuthenticationPrincipal AuthUser principal,
            @RequestParam(required = false) String q,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ApiResponse.ok(PageResponse.from(customerService.list(principal.companyId(), q, pageable)));
    }

    @GetMapping("/{id}")
    public ApiResponse<CustomerResponse> get(@AuthenticationPrincipal AuthUser principal, @PathVariable UUID id) {
        return ApiResponse.ok(customerService.get(principal.companyId(), id));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<CustomerResponse>> create(
            @AuthenticationPrincipal AuthUser principal, @Valid @RequestBody CreateCustomerRequest request) {
        CustomerResponse created = customerService.create(principal.companyId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(created));
    }

    @PutMapping("/{id}")
    public ApiResponse<CustomerResponse> update(
            @AuthenticationPrincipal AuthUser principal,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateCustomerRequest request) {
        return ApiResponse.ok(customerService.update(principal.companyId(), id, request));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal AuthUser principal, @PathVariable UUID id) {
        customerService.delete(principal.companyId(), id);
    }
}
