package com.genchat.foundation;

import com.agent.foundation.core.SpringAiChatModelAdapter;
import com.agent.foundation.tool.ToolDefinition;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class OpenAiOptionsCompatibilityTest {

    @ParameterizedTest
    @CsvSource({"true,true,false", "true,false,false", "false,true,false", "false,false,false",
            "true,true,true", "true,false,true", "false,true,true", "false,false,true"})
    void preservesProviderOptionsThroughRealRequestCreation(boolean streaming, boolean withTools,
                                                            boolean singleToolTransport) throws Exception {
        var json = new ObjectMapper();
        var capturedRequest = new AtomicReference<JsonNode>();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            try (exchange) {
                capturedRequest.set(json.readTree(exchange.getRequestBody()));
                boolean requestStreaming = capturedRequest.get().path("stream").asBoolean();
                String response = requestStreaming
                        ? """
                        data: {"id":"test","object":"chat.completion.chunk","created":0,"model":"test-model","choices":[{"index":0,"delta":{"role":"assistant","content":"ok"},"finish_reason":"stop"}]}

                        data: [DONE]

                        """
                        : """
                        {"id":"test","object":"chat.completion","created":0,"model":"test-model","choices":[{"index":0,"message":{"role":"assistant","content":"ok"},"finish_reason":"stop"}]}
                        """;
                byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", requestStreaming ? "text/event-stream" : "application/json");
                exchange.sendResponseHeaders(200, bytes.length);
                exchange.getResponseBody().write(bytes);
            }
        });
        server.start();
        try {
            ToolCallback defaultTool = tool("default_tool");
            var defaults = OpenAiChatOptions.builder()
                    .apiKey("local-test-key")
                    .baseUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/v1")
                    .model("test-model")
                    .temperature(0.3)
                    .reasoningEffort("low")
                    .maxRetries(0)
                    .timeout(Duration.ofSeconds(5))
                    .toolCallbacks(defaultTool)
                    .parallelToolCalls(true)
                    .build();
            var model = OpenAiChatModel.builder().options(defaults).build();
            var adapter = new SpringAiChatModelAdapter(singleToolTransport
                    ? new OpenAiToolCallFallbackChatModel(model) : model);
            List<Message> messages = List.of(new UserMessage("hello"));

            if (streaming) {
                ToolCallback[] callbacks = withTools ? new ToolCallback[]{tool("request_tool")} : new ToolCallback[0];
                var chunks = adapter.reasonStream(messages, callbacks).collectList().block(Duration.ofSeconds(10));
                assertNotNull(chunks);
            } else {
                List<ToolDefinition> tools = withTools
                        ? List.of(new ToolDefinition("request_tool", "Request tool", false)) : List.of();
                assertNotNull(adapter.reason(messages, tools));
            }

            JsonNode request = capturedRequest.get();
            assertNotNull(request, "The real OpenAI model must reach HTTP request execution");
            assertEquals("test-model", request.path("model").asText());
            assertEquals(0.3, request.path("temperature").asDouble());
            assertEquals("low", request.path("reasoning_effort").asText());
            assertEquals(1, request.path("tools").size());
            assertEquals(withTools ? "request_tool" : "default_tool",
                    request.path("tools").get(0).path("function").path("name").asText());
            assertEquals(!singleToolTransport, request.path("parallel_tool_calls").booleanValue());
            assertSame(defaults, model.getOptions());
            assertEquals(List.of(defaultTool), defaults.getToolCallbacks());
            assertEquals(Boolean.TRUE, defaults.getParallelToolCalls());
        } finally {
            server.stop(0);
        }
    }

    private static ToolCallback tool(String name) {
        return FunctionToolCallback.builder(name, (String input) -> "unused")
                .description("Test tool")
                .inputType(String.class)
                .build();
    }
}
