package com.aipack.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
@Profile("!test")
public class OllamaAIProvider implements AIProvider {

    private static final Logger log = LoggerFactory.getLogger(OllamaAIProvider.class);

    private final RestClient restClient;
    private final String model;

    public OllamaAIProvider(AiProperties properties) {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory();
        factory.setReadTimeout(properties.timeout() == null ? Duration.ofSeconds(30) : properties.timeout());
        this.restClient = RestClient.builder()
                .baseUrl(properties.ollama().baseUrl())
                .requestFactory(factory)
                .build();
        this.model = properties.ollama().model();
    }

    @Override
    public AIResponse generate(AIRequest request) {
        long start = System.nanoTime();
        try {
            OllamaGenerateResponse body = restClient
                    .post()
                    .uri("/api/generate")
                    .body(new OllamaGenerateRequest(model, request.prompt(), false, "json"))
                    .retrieve()
                    .body(OllamaGenerateResponse.class);
            int latency = (int) Duration.ofNanos(System.nanoTime() - start).toMillis();
            String text = body == null || body.response() == null ? "" : body.response();
            if (text.isBlank()) {
                throw new AiUnavailableException("Réponse Ollama vide");
            }
            return new AIResponse(text, "OLLAMA", model, latency, false, "SUCCESS");
        } catch (AiUnavailableException ex) {
            throw ex;
        } catch (ResourceAccessException ex) {
            throw new AiUnavailableException("Ollama injoignable", ex);
        } catch (RestClientException ex) {
            log.warn("Échec appel Ollama : {}", ex.getMessage());
            throw new AiUnavailableException("Échec appel Ollama", ex);
        }
    }

    private record OllamaGenerateRequest(String model, String prompt, boolean stream, String format) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record OllamaGenerateResponse(String response) {}
}
