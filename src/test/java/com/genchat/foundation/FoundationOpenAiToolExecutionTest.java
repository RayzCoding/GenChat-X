package com.genchat.foundation;

import com.agent.foundation.model.AgentStreamEvent;
import com.agent.foundation.model.RunnableParams;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.tool.function.FunctionToolCallback;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FoundationOpenAiToolExecutionTest {

    @Test
    void streamExecutesToolExactlyOnceAndCompletesThroughTheProductionFactory() throws Exception {
        var json = new ObjectMapper();
        var requests = new CopyOnWriteArrayList<JsonNode>();
        var executions = new AtomicInteger();
        var inputs = new CopyOnWriteArrayList<String>();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            try (exchange) {
                requests.add(json.readTree(exchange.getRequestBody()));
                boolean first = requests.size() == 1;
                String message = first
                        ? """
                        {"role":"assistant","content":"I will look that up.","tool_calls":[{"id":"call_1","type":"function","function":{"name":"lookup","arguments":"{\\"query\\":\\"hello\\"}"}}]}
                        """
                        : """
                        {"role":"assistant","content":"lookup complete"}
                        """;
                String response = """
                        {"id":"test","object":"chat.completion","created":0,"model":"test-model","choices":[{"index":0,"message":%s,"finish_reason":"%s"}]}
                        """.formatted(message, first ? "tool_calls" : "stop");
                byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, bytes.length);
                exchange.getResponseBody().write(bytes);
            }
        });
        server.start();
        try {
            var callback = FunctionToolCallback.builder("lookup", (LookupInput input) -> {
                executions.incrementAndGet();
                inputs.add(input.query());
                return "lookup-result";
            }).description("Lookup a query").inputType(LookupInput.class).build();
            var model = OpenAiChatModel.builder().options(OpenAiChatOptions.builder()
                    .apiKey("local-test-key")
                    .baseUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/v1")
                    .model("test-model").maxRetries(0).timeout(Duration.ofSeconds(5)).build()).build();
            var agent = FoundationReactAgentFactory.create(model, List.of(callback), 3, 1, false);

            var events = agent.streamForEvents("hello",
                            RunnableParams.builder().conversationId("tool-merge-regression").build())
                    .collectList().block(Duration.ofSeconds(10));

            assertNotNull(events);
            assertFalse(events.stream().anyMatch(event -> event.type() == AgentStreamEvent.Type.ERROR));
            assertEquals(1, events.stream().filter(event -> event.type() == AgentStreamEvent.Type.TOOL_EXECUTED).count());
            assertEquals(AgentStreamEvent.Type.COMPLETED, events.getLast().type());
            assertEquals("lookup complete", events.getLast().content());
            assertEquals(1, executions.get());
            assertEquals(List.of("hello"), inputs);
            assertEquals(2, requests.size());
            assertTrue(requests.stream().noneMatch(request -> request.path("stream").asBoolean()));
            assertTrue(requests.getLast().path("messages").toString().contains("lookup-result"));
        } finally {
            server.stop(0);
        }
    }

    record LookupInput(String query) {
    }
}
