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

class AnthropicAIProviderTest {

    private HttpServer server;
    private String baseUrl;
    private final AtomicReference<String> lastApiKey = new AtomicReference<>();
    private final AtomicReference<String> lastVersion = new AtomicReference<>();
    private final AtomicReference<String> lastBody = new AtomicReference<>();

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/messages", exchange -> {
            lastApiKey.set(exchange.getRequestHeaders().getFirst("x-api-key"));
            lastVersion.set(exchange.getRequestHeaders().getFirst("anthropic-version"));
            lastBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] response =
                    """
                    {"content":[{"type":"text","text":"{\\"score\\":91,\\"summary\\":\\"claude-ok\\"}"}]}
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
    void generatePostsMessagesAndReturnsText() {
        AnthropicAIProvider provider = new AnthropicAIProvider(props("sk-ant-test"));
        AIResponse response =
                provider.generate(new AIRequest(UUID.randomUUID(), "lead-qualification", "{\"hello\":true}"));

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.provider()).isEqualTo("ANTHROPIC");
        assertThat(response.model()).isEqualTo("claude-3-5-haiku-latest");
        assertThat(response.text()).contains("\"score\":91");
        assertThat(lastApiKey.get()).isEqualTo("sk-ant-test");
        assertThat(lastVersion.get()).isEqualTo("2023-06-01");
        assertThat(lastBody.get()).contains("\"system\"").contains("\"messages\"");
    }

    @Test
    void requiresApiKey() {
        assertThatThrownBy(() -> new AnthropicAIProvider(props(" ")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("AI_ANTHROPIC_API_KEY");
    }

    @Test
    void normalizedProviderAliasesClaude() {
        assertThat(new AiProperties("claude", null, null, null, null, null).normalizedProvider())
                .isEqualTo("anthropic");
    }

    private AiProperties props(String apiKey) {
        return new AiProperties(
                "anthropic",
                Duration.ofSeconds(5),
                new AiProperties.Ollama("http://localhost:11434", "llama3.2"),
                new AiProperties.OpenAi("https://api.openai.com/v1", "", "gpt-4o-mini", true),
                new AiProperties.Anthropic(baseUrl, apiKey, "claude-3-5-haiku-latest"),
                new AiProperties.Cache(false, Duration.ofMinutes(1), "localhost", 6379));
    }
}
