package com.aipack.ai;

import com.aipack.identity.Company;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import jakarta.persistence.EntityManager;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AiGateway {

    private static final Logger log = LoggerFactory.getLogger(AiGateway.class);

    private final AIProvider aiProvider;
    private final AiResponseCache cache;
    private final AiRequestLogRepository aiRequestLogRepository;
    private final EntityManager entityManager;
    private final Retry retry;
    private final CircuitBreaker circuitBreaker;

    public AiGateway(
            AIProvider aiProvider,
            AiResponseCache cache,
            AiRequestLogRepository aiRequestLogRepository,
            EntityManager entityManager) {
        this.aiProvider = aiProvider;
        this.cache = cache;
        this.aiRequestLogRepository = aiRequestLogRepository;
        this.entityManager = entityManager;
        this.retry = Retry.of(
                "ai",
                RetryConfig.custom()
                        .maxAttempts(2)
                        .intervalFunction(IntervalFunction.ofExponentialBackoff(Duration.ofMillis(200), 2.0))
                        .retryOnException(ex -> ex instanceof AiUnavailableException)
                        .build());
        this.circuitBreaker = CircuitBreaker.of(
                "ai",
                CircuitBreakerConfig.custom()
                        .slidingWindowSize(10)
                        .failureRateThreshold(50f)
                        .waitDurationInOpenState(Duration.ofSeconds(30))
                        .build());
    }

    @Transactional
    public AIResponse generate(AIRequest request) {
        String promptHash = sha256(request.purpose() + "\n" + request.prompt());
        String cacheKey = "ai:" + request.purpose() + ":" + promptHash;
        long start = System.nanoTime();

        var cached = cache.get(cacheKey);
        if (cached.isPresent()) {
            AIResponse hit = new AIResponse(cached.get(), "CACHE", null, 0, true, "SUCCESS");
            logRequest(request, promptHash, hit, null);
            return hit;
        }

        try {
            Supplier<AIResponse> decorated = CircuitBreaker.decorateSupplier(
                    circuitBreaker, Retry.decorateSupplier(retry, () -> aiProvider.generate(request)));
            AIResponse response = decorated.get();
            if (response.isSuccess()
                    && response.text() != null
                    && !response.text().isBlank()
                    && !"document-extraction".equals(request.purpose())) {
                cache.put(cacheKey, response.text());
            }
            logRequest(request, promptHash, response, null);
            return response;
        } catch (CallNotPermittedException | AiUnavailableException ex) {
            int latency = (int) Duration.ofNanos(System.nanoTime() - start).toMillis();
            AIResponse unavailable = AIResponse.unavailable("OLLAMA", null, latency);
            logRequest(request, promptHash, unavailable, ex.getMessage());
            log.warn("IA indisponible (purpose={}): {}", request.purpose(), ex.getMessage());
            return unavailable;
        } catch (RuntimeException ex) {
            int latency = (int) Duration.ofNanos(System.nanoTime() - start).toMillis();
            AIResponse unavailable = AIResponse.unavailable("OLLAMA", null, latency);
            logRequest(request, promptHash, unavailable, ex.getMessage());
            log.warn("Échec inattendu IA (purpose={}): {}", request.purpose(), ex.getMessage());
            return unavailable;
        }
    }

    private void logRequest(AIRequest request, String promptHash, AIResponse response, String errorMessage) {
        AiRequestLog logEntry = new AiRequestLog();
        logEntry.setCompany(entityManager.getReference(Company.class, request.companyId()));
        logEntry.setProvider(response.provider() == null ? "UNKNOWN" : response.provider());
        logEntry.setModel(response.model());
        logEntry.setPurpose(request.purpose());
        logEntry.setPromptHash(promptHash);
        logEntry.setStatus(response.status());
        logEntry.setLatencyMs(response.latencyMs());
        logEntry.setCacheHit(response.cacheHit());
        logEntry.setErrorMessage(truncate(errorMessage, 2000));
        aiRequestLogRepository.save(logEntry);
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }
}
