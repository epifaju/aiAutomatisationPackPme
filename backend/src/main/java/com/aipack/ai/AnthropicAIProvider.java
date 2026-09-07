package com.aipack.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Client API Messages Anthropic ({@code /v1/messages}) — pas compatible OpenAI.
 */
public class AnthropicAIProvider implements AIProvider {

    private static final Logger log = LoggerFactory.getLogger(AnthropicAIProvider.class);
    private static final String PROVIDER_NAME = "ANTHROPIC";
    private static final String ANTHROPIC_VERSION = "2023-06-01";
    private static final String SYSTEM_JSON =
            "You are a helpful assistant. Respond with valid JSON only, no markdown.";

    private final RestClient restClient;
    private final String model;

    public AnthropicAIProvider(AiProperties properties) {
        AiProperties.Anthropic anthropic = properties.anthropic();
        if (anthropic == null || !StringUtils.hasText(anthropic.apiKey())) {
            throw new IllegalStateException(
                    "AI_PROVIDER=anthropic requires AI_ANTHROPIC_API_KEY (app.ai.anthropic.api-key)");
        }
        String baseUrl = anthropic.baseUrl() == null || anthropic.baseUrl().isBlank()
                ? "https://api.anthropic.com"
                : anthropic.baseUrl().replaceAll("/+$", "");
        // Accept either https://api.anthropic.com or .../v1
        if (baseUrl.endsWith("/v1")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 3);
        }
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory();
        factory.setReadTimeout(properties.timeout() == null ? Duration.ofSeconds(60) : properties.timeout());
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("x-api-key", anthropic.apiKey().trim())
                .defaultHeader("anthropic-version", ANTHROPIC_VERSION)
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .requestFactory(factory)
                .build();
        this.model = StringUtils.hasText(anthropic.model())
                ? anthropic.model().trim()
                : "claude-3-5-haiku-latest";
    }

    @Override
    public AIResponse generate(AIRequest request) {
        long start = System.nanoTime();
        try {
            MessagesResponse body = restClient
                    .post()
                    .uri("/v1/messages")
                    .body(buildRequest(request.prompt()))
                    .retrieve()
                    .body(MessagesResponse.class);
            int latency = (int) Duration.ofNanos(System.nanoTime() - start).toMillis();
            String text = extractText(body);
            if (text.isBlank()) {
                throw new AiUnavailableException("Réponse Anthropic vide");
            }
            return new AIResponse(text, PROVIDER_NAME, model, latency, false, "SUCCESS");
        } catch (AiUnavailableException ex) {
            throw ex;
        } catch (RestClientResponseException ex) {
            log.warn("Échec appel Anthropic (HTTP {}): {}", ex.getStatusCode().value(), ex.getMessage());
            throw new AiUnavailableException("Échec appel Anthropic (HTTP " + ex.getStatusCode().value() + ")", ex);
        } catch (ResourceAccessException ex) {
            throw new AiUnavailableException("Anthropic injoignable", ex);
        } catch (RestClientException ex) {
            log.warn("Échec appel Anthropic : {}", ex.getMessage());
            throw new AiUnavailableException("Échec appel Anthropic", ex);
        }
    }

    private Map<String, Object> buildRequest(String prompt) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("max_tokens", 1024);
        body.put("temperature", 0.2);
        body.put("system", SYSTEM_JSON);
        body.put("messages", List.of(Map.of("role", "user", "content", prompt == null ? "" : prompt)));
        return body;
    }

    private static String extractText(MessagesResponse body) {
        if (body == null || body.content() == null || body.content().isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (ContentBlock block : body.content()) {
            if (block != null && "text".equals(block.type()) && block.text() != null) {
                if (!sb.isEmpty()) {
                    sb.append('\n');
                }
                sb.append(block.text());
            }
        }
        return sb.toString().trim();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record MessagesResponse(List<ContentBlock> content) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ContentBlock(String type, String text) {}
}
