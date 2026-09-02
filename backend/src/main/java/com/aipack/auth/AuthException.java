package com.aipack.auth;

import org.springframework.http.HttpStatus;

public class AuthException extends RuntimeException {

    private final String code;
    private final HttpStatus status;

    public AuthException(String code, String message, HttpStatus status) {
        super(message);
        this.code = code;
        this.status = status;
    }

    public static AuthException unauthorized() {
        return new AuthException("UNAUTHORIZED", "Identifiants invalides", HttpStatus.UNAUTHORIZED);
    }

    public static AuthException invalidToken() {
        return new AuthException("INVALID_TOKEN", "Jeton invalide ou expiré", HttpStatus.UNAUTHORIZED);
    }

    public String getCode() {
        return code;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
