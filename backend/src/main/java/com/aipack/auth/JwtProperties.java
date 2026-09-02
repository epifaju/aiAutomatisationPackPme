package com.aipack.auth;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(String secret, Duration accessTokenTtl, Duration refreshTokenTtl) {}
