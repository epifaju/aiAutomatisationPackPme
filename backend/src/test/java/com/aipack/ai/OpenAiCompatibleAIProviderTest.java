package com.aipack.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OpenAiCompatibleAIProviderTest {

    private HttpServer server;
    private String baseUrl;
    private final AtomicReference<String> lastAuth = new AtomicReference<>();
    private final AtomicReference<String> lastBody = new AtomicReference<>();

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/chat/completions", exchange -> {
            lastAuth.set(exchange.getRequestHeaders().getFirst("Authorization"));
            lastBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] response =
                    """
                    {"choices":[{"message":{"role":"assistant","content":"{\\"score\\":88,\\"summary\\":\\"ok\\"}"}}]}
                    """
                            .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(response);
            }
        });
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void generatePostsChatCompletionsAndReturnsContent() {
        OpenAiCompatibleAIProvider provider = new OpenAiCompatibleAIProvider(props("test-key", true));
        AIResponse response =
                provider.generate(new AIRequest(UUID.randomUUID(), "lead-qualification", "{\"hello\":true}"));

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.provider()).isEqualTo("OPENAI");
        assertThat(response.model()).isEqualTo("gpt-4o-mini");
        assertThat(response.text()).contains("\"score\":88");
        assertThat(lastAuth.get()).isEqualTo("Bearer test-key");
        assertThat(lastBody.get()).contains("\"response_format\"").contains("json_object");
    }

    @Test
    void generateOmitsJsonModeWhenDisabled() {
        OpenAiCompatibleAIProvider provider = new OpenAiCompatibleAIProvider(props("test-key", false));
        provider.generate(new AIRequest(UUID.randomUUID(), "lead-qualification", "prompt"));
        assertThat(lastBody.get()).doesNotContain("response_format");
    }

    @Test
    void requiresApiKey() {
        assertThatThrownBy(() -> new OpenAiCompatibleAIProvider(props("  ", true)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("AI_OPENAI_API_KEY");
    }

    @Test
    void normalizedProviderAliasesOpenai() {
        assertThat(new AiProperties("openai-compatible", null, null, null, null).normalizedProvider())
                .isEqualTo("openai");
        assertThat(new AiProperties("external", null, null, null, null).normalizedProvider()).isEqualTo("openai");
        assertThat(new AiProperties(null, null, null, null, null).normalizedProvider()).isEqualTo("ollama");
    }

    private AiProperties props(String apiKey, boolean jsonMode) {
        return new AiProperties(
                "openai",
                Duration.ofSeconds(5),
                new AiProperties.Ollama("http://localhost:11434", "llama3.2"),
                new AiProperties.OpenAi(baseUrl, apiKey, "gpt-4o-mini", jsonMode),
                new AiProperties.Cache(false, Duration.ofMinutes(1), "localhost", 6379));
    }
}
