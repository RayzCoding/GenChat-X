package com.genchat.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.genchat.foundation.FoundationMultimodalInterpreter;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.openai.autoconfigure.OpenAiChatAutoConfiguration;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySourcesPropertyResolver;
import org.springframework.core.io.ClassPathResource;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.LinkedBlockingQueue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class ChatModelConfigurationTest {

    private static final String UNICODE_TEXT = "📄 文件内容总结（Staffing团队介绍 —— Inspire主题色更新版）";

    @ParameterizedTest
    @ValueSource(strings = {"/v1", "/compatible-mode/v1", "/compatible-mode/v1/"})
    void usesVersionedEndpointAndSeparatesChatFromVision(String basePath) throws Exception {
        String expectedPath = basePath.replaceAll("/+$", "") + "/chat/completions";
        var json = new ObjectMapper();
        var requests = new LinkedBlockingQueue<CapturedRequest>();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            try (exchange) {
                JsonNode body = json.readTree(exchange.getRequestBody());
                String path = exchange.getRequestURI().getPath();
                requests.add(new CapturedRequest(path, body));
                boolean streaming = body.path("stream").asBoolean();
                boolean validPath = expectedPath.equals(path);
                String response = !validPath ? "{\"error\":{\"message\":\"Wrong endpoint\"}}"
                        : streaming ? """
                        data: {"id":"test","object":"chat.completion.chunk","created":0,"model":"test-model","choices":[{"index":0,"delta":{"role":"assistant","content":"ok"},"finish_reason":"stop"}]}

                        data: [DONE]

                        """ : """
                        {"id":"test","object":"chat.completion","created":0,"model":"test-model","choices":[{"index":0,"message":{"role":"assistant","content":"ok"},"finish_reason":"stop"}]}
                        """;
                response = response.replace("\"content\":\"ok\"", "\"content\":\"" + UNICODE_TEXT + "\"");
                byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type",
                        streaming && validPath ? "text/event-stream" : "application/json");
                exchange.sendResponseHeaders(validPath ? 200 : 404, bytes.length);
                exchange.getResponseBody().write(bytes);
            }
        });
        server.start();
        try {
            new ApplicationContextRunner()
                    .withConfiguration(AutoConfigurations.of(OpenAiChatAutoConfiguration.class))
                    .withUserConfiguration(MultimodalChatModelConfig.class,
                            FoundationMultimodalInterpreter.class, InjectionConfiguration.class)
                    .withBean(ToolCallingManager.class, () -> mock(ToolCallingManager.class))
                    .withPropertyValues(
                            "spring.ai.openai.api-key=local-test-key",
                            "spring.ai.openai.base-url=http://127.0.0.1:" + server.getAddress().getPort() + basePath,
                            "spring.ai.openai.timeout=5s",
                            "spring.ai.openai.max-retries=0",
                            "spring.ai.openai.chat.options.model=qwen-plus",
                            "spring.ai.openai.chat.options.temperature=0.7",
                            "spring.ai.openai.chat.options.max-tokens=5000",
                            "spring.ai.openai.multimodal.options.model=qwen3-vl-plus",
                            "spring.ai.openai.multimodal.options.temperature=0.2")
                    .run(context -> {
                        assertThat(context).hasNotFailed();
                        var chat = context.getBean("openAiChatModel", OpenAiChatModel.class);
                        var vision = context.getBean("multimodalChatModel", OpenAiChatModel.class);
                        assertThat(context.getBean(ChatConsumer.class).model()).isSameAs(chat);
                        assertThat(chat.getOptions().getModel()).isEqualTo("qwen-plus");
                        assertThat(chat.getOptions().getTemperature()).isEqualTo(0.7);
                        assertThat(chat.getOptions().getMaxTokens()).isEqualTo(5000);
                        assertThat(vision.getOptions().getModel()).isEqualTo("qwen3-vl-plus");

                        assertThat(chat.call("hello")).isEqualTo(UNICODE_TEXT);
                        assertRequest(requests.remove(), expectedPath, "qwen-plus", false);
                        assertThat(streamText(chat)).isEqualTo(UNICODE_TEXT);
                        assertRequest(requests.remove(), expectedPath, "qwen-plus", true);

                        var interpreter = context.getBean(FoundationMultimodalInterpreter.class);
                        assertThat(interpreter.describeImage(new byte[]{1, 2, 3})).isEqualTo(UNICODE_TEXT);
                        assertRequest(requests.remove(), expectedPath, "qwen3-vl-plus", false);
                        assertThat(streamText(vision)).isEqualTo(UNICODE_TEXT);
                        assertRequest(requests.remove(), expectedPath, "qwen3-vl-plus", true);
                        assertThat(requests).isEmpty();
                    });
        } finally {
            server.stop(0);
        }
    }

    @Test
    void defaultChatAndEmbeddingUrlsIncludeSdkVersionPrefix() throws Exception {
        var sources = new MutablePropertySources();
        new YamlPropertySourceLoader().load("application", new ClassPathResource("application.yml"))
                .forEach(sources::addLast);
        var resolver = new PropertySourcesPropertyResolver(sources);

        assertThat(resolver.getProperty("spring.ai.openai.base-url"))
                .isEqualTo("https://api.openai.com/v1");
        assertThat(resolver.getProperty("spring.ai.openai.embedding.base-url"))
                .isEqualTo("https://api.openai.com/v1");
    }

    private static String streamText(ChatModel model) {
        return model.stream(new Prompt("hello"))
                .filter(response -> response.getResult() != null && response.getResult().getOutput().getText() != null)
                .map(response -> response.getResult().getOutput().getText())
                .reduce("", String::concat).block(Duration.ofSeconds(10));
    }

    private static void assertRequest(CapturedRequest request, String path, String model, boolean streaming) {
        assertThat(request.path()).isEqualTo(path);
        assertThat(request.body().path("model").asText()).isEqualTo(model);
        assertThat(request.body().path("stream").asBoolean()).isEqualTo(streaming);
    }

    private record CapturedRequest(String path, JsonNode body) {
    }

    private record ChatConsumer(ChatModel model) {
    }

    @Configuration(proxyBeanMethods = false)
    static class InjectionConfiguration {
        @Bean
        ChatConsumer chatConsumer(ChatModel chatModel) {
            return new ChatConsumer(chatModel);
        }
    }
}
