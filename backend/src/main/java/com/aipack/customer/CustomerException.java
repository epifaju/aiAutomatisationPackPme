package com.aipack.customer;

import org.springframework.http.HttpStatus;

public class CustomerException extends RuntimeException {

    private final String code;
    private final HttpStatus status;

    public CustomerException(String code, String message, HttpStatus status) {
        super(message);
        this.code = code;
        this.status = status;
    }

    public static CustomerException notFound() {
        return new CustomerException("CUSTOMER_NOT_FOUND", "Client introuvable", HttpStatus.NOT_FOUND);
    }

    public static CustomerException inUse() {
        return new CustomerException(
                "CUSTOMER_IN_USE", "Impossible de supprimer un client qui a des factures", HttpStatus.CONFLICT);
    }

    public String getCode() {
        return code;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
