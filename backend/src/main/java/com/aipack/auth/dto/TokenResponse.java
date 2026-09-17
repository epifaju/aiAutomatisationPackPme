package com.aipack.auth.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record TokenResponse(String accessToken, String refreshToken, String tokenType, long expiresIn) {

    public TokenResponse withoutRefreshToken() {
        return new TokenResponse(accessToken, null, tokenType, expiresIn);
    }
}
