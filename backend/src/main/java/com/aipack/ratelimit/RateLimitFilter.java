package com.aipack.ratelimit;

import com.aipack.common.api.ApiError;
import com.aipack.common.api.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * In-memory Bucket4j limits for public auth and webhook endpoints (per client IP).
 * Single-replica Compose deploy; Redis-backed limiting can wait for multi-instance.
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private final RateLimitProperties properties;
    private final ObjectMapper objectMapper;
    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    public RateLimitFilter(RateLimitProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!properties.enabled() || !HttpMethod.POST.matches(request.getMethod())) {
            return true;
        }
        String path = normalizedPath(request);
        return resolveGroup(path) == null;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String path = normalizedPath(request);
        String group = resolveGroup(path);
        if (group == null) {
            filterChain.doFilter(request, response);
            return;
        }

        RateLimitProperties.Limit limit =
                "auth".equals(group) ? properties.auth() : properties.webhook();
        String key = group + ":" + clientIp(request);
        Bucket bucket = buckets.computeIfAbsent(key, ignored -> newBucket(limit));
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
        if (probe.isConsumed()) {
            response.setHeader("X-RateLimit-Remaining", String.valueOf(probe.getRemainingTokens()));
            filterChain.doFilter(request, response);
            return;
        }

        long waitSeconds = Math.max(1, Duration.ofNanos(probe.getNanosToWaitForRefill()).toSeconds());
        response.setStatus(429);
        response.setHeader("Retry-After", String.valueOf(waitSeconds));
        response.setHeader("X-RateLimit-Remaining", "0");
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(
                response.getOutputStream(),
                ApiResponse.failure(ApiError.of(
                        "RATE_LIMIT_EXCEEDED",
                        "Trop de requêtes. Réessayez dans " + waitSeconds + " s.")));
    }

    private static Bucket newBucket(RateLimitProperties.Limit limit) {
        Bandwidth bandwidth = Bandwidth.builder()
                .capacity(limit.capacity())
                .refillGreedy(limit.capacity(), limit.refillPeriod())
                .build();
        return Bucket.builder().addLimit(bandwidth).build();
    }

    private String clientIp(HttpServletRequest request) {
        if (properties.trustForwardedHeaders()) {
            String forwarded = request.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                return forwarded.split(",")[0].trim();
            }
        }
        String remote = request.getRemoteAddr();
        return remote == null || remote.isBlank() ? "unknown" : remote;
    }

    private static String resolveGroup(String path) {
        if ("/api/v1/auth/login".equals(path) || "/api/v1/auth/refresh".equals(path)) {
            return "auth";
        }
        if (path.startsWith("/webhook/")) {
            return "webhook";
        }
        return null;
    }

    private static String normalizedPath(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String context = request.getContextPath();
        if (context != null && !context.isEmpty() && uri.startsWith(context)) {
            uri = uri.substring(context.length());
        }
        if (uri.length() > 1 && uri.endsWith("/")) {
            return uri.substring(0, uri.length() - 1);
        }
        return uri;
    }

    /** Clears in-memory buckets (tests). */
    public void clearBuckets() {
        buckets.clear();
    }
}
