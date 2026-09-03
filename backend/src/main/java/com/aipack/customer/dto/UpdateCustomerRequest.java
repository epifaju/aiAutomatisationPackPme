package com.aipack.customer.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

public record UpdateCustomerRequest(
        @Size(max = 255) String name,
        @Email @Size(max = 320) String email,
        @Size(max = 64) String phone,
        @Size(max = 4000) String address) {}
