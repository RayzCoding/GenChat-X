package com.genchat.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import com.openai.client.OpenAIClientImpl;
import com.openai.core.ClientOptions;
import org.springframework.ai.openai.http.okhttp.SpringAiOpenAiHttpClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class MultimodalChatModelConfig {
    @Value("${spring.ai.openai.api-key}")
    private String apiKey;
    @Value("${spring.ai.openai.base-url}")
    private String baseUrl;
    @Value("${spring.ai.openai.multimodal.options.model}")
    private String model;
    @Value("${spring.ai.openai.multimodal.options.temperature}")
    private Double temperature;


    // Keep the regular chat model auto-configuration active; this model is opt-in only.
    @Bean(defaultCandidate = false)
    public OpenAiChatModel multimodalChatModel() {
        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .temperature(temperature)
                .model(model)
                .build();
        OpenAIClientImpl openAiClient = new OpenAIClientImpl(ClientOptions.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .httpClient(SpringAiOpenAiHttpClient.builder().build())
                .build());
        OpenAiChatModel model = OpenAiChatModel.builder()
                .openAiClient(openAiClient)
                .openAiClientAsync(openAiClient.async())
                .options(options)
                .build();
        log.info("Multimodal chat model initialized successfully.");
        return model;
    }
}
