package com.genchat.foundation;

import com.agent.foundation.output.StructuredOutputSpec;
import com.agent.foundation.output.StructuredOutputSupport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.tool.function.FunctionToolCallback;

import static org.junit.jupiter.api.Assertions.*;

class OpenAiStructuredOutputTest {
    public record Reply(String value) { }
    public record ToolInput(String query) { }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void structuredRequestsHaveNoToolsPreserveOptionsAndOptionallySendNativeSchema(boolean nativeOutput) throws Exception {
        var json = new ObjectMapper();
        var captured = new AtomicReference<JsonNode>();
        var executions = new AtomicInteger();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            try (exchange) {
                captured.set(json.readTree(exchange.getRequestBody()));
                String body = json.writeValueAsString(Map.of("id", "test", "object", "chat.completion", "created", 0,
                        "model", "test-model", "choices", List.of(Map.of("index", 0, "finish_reason", "stop",
                                "message", Map.of("role", "assistant", "content", "{\"value\":\"ok\"}")))));
                byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, bytes.length);
                exchange.getResponseBody().write(bytes);
            }
        });
        server.start();
        try {
            var callback = FunctionToolCallback.builder("lookup", (ToolInput input) -> {
                executions.incrementAndGet();
                return "unused";
            }).description("lookup").inputType(ToolInput.class).build();
            var defaults = OpenAiChatOptions.builder().apiKey("local-test-key")
                    .baseUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/v1")
                    .model("test-model").temperature(0.3).reasoningEffort("low")
                    .maxRetries(0).timeout(Duration.ofSeconds(5)).toolCallbacks(callback).build();
            var adapter = new ToolCallbackAwareChatModelAdapter(OpenAiChatModel.builder().options(defaults).build(), List.of(callback));
            List<Message> messages = List.of(new UserMessage("test"));
            adapter.reason(messages, List.of());
            JsonNode expectedSchema = json.readTree(callback.getToolDefinition().inputSchema());
            JsonNode actualSchema = captured.get().path("tools").get(0).path("function").path("parameters");
            expectedSchema.fields().forEachRemaining(field -> assertEquals(field.getValue(), actualSchema.get(field.getKey())));

            var spec = StructuredOutputSpec.of(Reply.class).withNativeOutput(nativeOutput);
            assertEquals(new Reply("ok"), StructuredOutputSupport.generate(adapter, messages, spec));
            JsonNode request = captured.get();
            assertEquals(0, request.path("tools").size(), "Neither model-default nor GenChat callbacks may leak into finalization");
            assertEquals("test-model", request.path("model").asText());
            assertEquals(0.3, request.path("temperature").asDouble());
            assertEquals("low", request.path("reasoning_effort").asText());
            if (nativeOutput) {
                assertEquals("json_schema", request.path("response_format").path("type").asText());
                assertTrue(request.path("response_format").path("json_schema").path("schema").path("properties").has("value"));
            }
            assertEquals(List.of(callback), defaults.getToolCallbacks());
            assertEquals(0, executions.get());
        } finally {
            server.stop(0);
        }
    }
}


