package com.genchat.foundation;

import com.agent.foundation.core.ReasoningStreamChunk;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.tool.function.FunctionToolCallback;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class OpenAiToolCallFallbackTest {

    @ParameterizedTest
    @ValueSource(strings = {"late-name", "repeated-id", "text-before-tools", "reasoning-before-tools", "normal"})
    void bypassesChunkMergerAndPreservesToolRequest(String scenario) throws Exception {
        var json = new ObjectMapper();
        var requests = new CopyOnWriteArrayList<JsonNode>();
        var executions = new AtomicInteger();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            try (exchange) {
                JsonNode request = json.readTree(exchange.getRequestBody());
                requests.add(request);
                boolean streaming = request.path("stream").asBoolean();
                boolean lateName = !scenario.equals("normal") && !scenario.equals("repeated-id");
                String firstFunction = lateName
                        ? "\"arguments\":\"\"" : "\"name\":\"lookup\",\"arguments\":\"\"";
                String nextId = scenario.equals("repeated-id") ? "\"id\":\"call_1\"," : "";
                String nextName = lateName ? "\"name\":\"lookup\"," : "";
                String preamble = switch (scenario) {
                    case "text-before-tools" -> chunk("{\"content\":\"Let me look that up.\"}", "null");
                    case "reasoning-before-tools" -> chunk("{\"reasoning_content\":\"Need a lookup.\"}", "null");
                    default -> "";
                };
                String response = streaming
                        ? chunk("{\"role\":\"assistant\"}", "null") + preamble
                            + chunk("{\"tool_calls\":[{\"index\":0,\"id\":\"call_1\",\"type\":\"function\",\"function\":{" + firstFunction + "}}]}", "null")
                            + chunk("{\"tool_calls\":[{\"index\":0," + nextId + "\"function\":{" + nextName + "\"arguments\":\"{\\\"query\\\":\\\"hello\\\"}\"}}]}", "null")
                            + chunk("{}", "\"tool_calls\"") + "data: [DONE]\n\n"
                        : """
                        {"id":"test","object":"chat.completion","created":0,"model":"test-model","choices":[{"index":0,"message":{"role":"assistant","content":null,"tool_calls":[{"id":"call_1","type":"function","function":{"name":"lookup","arguments":"{\\"query\\":\\"hello\\"}"}}]},"finish_reason":"tool_calls"}]}
                        """;
                byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", streaming ? "text/event-stream" : "application/json");
                exchange.sendResponseHeaders(200, bytes.length);
                exchange.getResponseBody().write(bytes);
            }
        });
        server.start();
        try {
            var callback = FunctionToolCallback.builder("lookup", (LookupInput input) -> {
                executions.incrementAndGet();
                return "unused";
            }).description("Lookup a query").inputType(LookupInput.class).build();
            var defaults = OpenAiChatOptions.builder()
                    .apiKey("local-test-key")
                    .baseUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/v1")
                    .model("test-model").temperature(0.3).reasoningEffort("low")
                    .maxRetries(0).timeout(Duration.ofSeconds(5)).build();
            var model = OpenAiChatModel.builder().options(defaults).build();
            var adapter = new ToolCallbackAwareChatModelAdapter(model, List.of(callback));

            var chunks = adapter.reasonStream(List.of(new UserMessage("hello")), List.of())
                    .collectList().block(Duration.ofSeconds(10));

            assertNotNull(chunks);
            var decision = chunks.stream().map(ReasoningStreamChunk::decision)
                    .filter(java.util.Objects::nonNull).findFirst().orElseThrow();
            assertEquals("lookup", decision.toolInvocation().toolName());
            assertEquals("hello", decision.toolInvocation().arguments().get("query"));
            assertEquals(0, executions.get(), "Foundation, not the model fallback, must execute tools");
            assertEquals(1, requests.size(), "Do not first issue a streaming request that may already emit content");
            var request = requests.getFirst();
            assertFalse(request.path("stream").asBoolean());
            assertEquals("test-model", request.path("model").asText());
            assertEquals(0.3, request.path("temperature").asDouble());
            assertEquals("low", request.path("reasoning_effort").asText());
            assertEquals("hello", request.path("messages").get(0).path("content").asText());
            assertEquals("lookup", request.path("tools").get(0).path("function").path("name").asText());
            assertEquals("query", request.path("tools").get(0).path("function")
                    .path("parameters").path("properties").fieldNames().next());
        } finally {
            server.stop(0);
        }
    }

    private static String chunk(String delta, String finishReason) {
        return "data: {\"id\":\"test\",\"object\":\"chat.completion.chunk\",\"created\":0,\"model\":\"test-model\",\"choices\":[{\"index\":0,\"delta\":"
                + delta + ",\"finish_reason\":" + finishReason + "}]}\n\n";
    }

    record LookupInput(String query) {
    }
}

