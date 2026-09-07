package com.aipack.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Client Chat Completions compatible OpenAI ({@code /chat/completions}).
 * Couvre OpenAI, Mistral (endpoint compatible), Azure OpenAI (base-url adaptée), etc.
 */
public class OpenAiCompatibleAIProvider implements AIProvider {

    private static final Logger log = LoggerFactory.getLogger(OpenAiCompatibleAIProvider.class);
    private static final String PROVIDER_NAME = "OPENAI";

    private final RestClient restClient;
    private final String model;
    private final boolean jsonMode;

    public OpenAiCompatibleAIProvider(AiProperties properties) {
        AiProperties.OpenAi openai = properties.openai();
        if (openai == null || !StringUtils.hasText(openai.apiKey())) {
            throw new IllegalStateException(
                    "AI_PROVIDER=openai requires AI_OPENAI_API_KEY (app.ai.openai.api-key)");
        }
        String baseUrl = openai.baseUrl() == null || openai.baseUrl().isBlank()
                ? "https://api.openai.com/v1"
                : openai.baseUrl().replaceAll("/+$", "");
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory();
        factory.setReadTimeout(properties.timeout() == null ? Duration.ofSeconds(60) : properties.timeout());
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + openai.apiKey().trim())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .requestFactory(factory)
                .build();
        this.model = StringUtils.hasText(openai.model()) ? openai.model().trim() : "gpt-4o-mini";
        this.jsonMode = openai.jsonMode() == null || openai.jsonMode();
    }

    @Override
    public AIResponse generate(AIRequest request) {
        long start = System.nanoTime();
        try {
            ChatCompletionResponse body = restClient
                    .post()
                    .uri("/chat/completions")
                    .body(buildRequest(request.prompt()))
                    .retrieve()
                    .body(ChatCompletionResponse.class);
            int latency = (int) Duration.ofNanos(System.nanoTime() - start).toMillis();
            String text = extractContent(body);
            if (text.isBlank()) {
                throw new AiUnavailableException("Réponse OpenAI vide");
            }
            return new AIResponse(text, PROVIDER_NAME, model, latency, false, "SUCCESS");
        } catch (AiUnavailableException ex) {
            throw ex;
        } catch (RestClientResponseException ex) {
            log.warn("Échec appel OpenAI (HTTP {}): {}", ex.getStatusCode().value(), ex.getMessage());
            throw new AiUnavailableException("Échec appel OpenAI (HTTP " + ex.getStatusCode().value() + ")", ex);
        } catch (ResourceAccessException ex) {
            throw new AiUnavailableException("OpenAI injoignable", ex);
        } catch (RestClientException ex) {
            log.warn("Échec appel OpenAI : {}", ex.getMessage());
            throw new AiUnavailableException("Échec appel OpenAI", ex);
        }
    }

    private Map<String, Object> buildRequest(String prompt) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put(
                "messages",
                List.of(
                        Map.of(
                                "role",
                                "system",
                                "content",
                                "You are a helpful assistant. Respond with valid JSON only, no markdown."),
                        Map.of("role", "user", "content", prompt == null ? "" : prompt)));
        body.put("temperature", 0.2);
        body.put("max_tokens", 1024);
        if (jsonMode) {
            body.put("response_format", Map.of("type", "json_object"));
        }
        return body;
    }

    private static String extractContent(ChatCompletionResponse body) {
        if (body == null || body.choices() == null || body.choices().isEmpty()) {
            return "";
        }
        ChatMessage message = body.choices().getFirst().message();
        if (message == null || message.content() == null) {
            return "";
        }
        return message.content().trim();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ChatCompletionResponse(List<ChatChoice> choices) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ChatChoice(ChatMessage message) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ChatMessage(String content) {}
}
