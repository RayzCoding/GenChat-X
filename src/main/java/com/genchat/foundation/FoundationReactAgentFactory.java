package com.genchat.foundation;

import com.agent.foundation.compression.ContextCompressionManager;
import com.agent.foundation.core.ReactAgent;
import com.agent.foundation.retry.RetrySupport;
import com.agent.foundation.tool.ToolSecurityPolicy;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;

import java.time.Duration;
import java.util.List;

/**
 * Creates foundation ReactAgent instances with GenChat defaults.
 */
public final class FoundationReactAgentFactory {

    private FoundationReactAgentFactory() {
    }

    public static ReactAgent create(ChatModel chatModel,
                                    List<ToolCallback> tools,
                                    int maxRounds,
                                    int maxRetries,
                                    boolean enableCompression) {
        ReactAgent.Builder builder = ReactAgent.builder()
                .chatModelAdapter(new ToolCallbackAwareChatModelAdapter(chatModel, tools))
                .toolExecutor(FoundationToolExecutorFactory.fromToolCallbacks(tools))
                .toolSecurityPolicy(ToolSecurityPolicy.permissive())
                .maxRounds(maxRounds > 0 ? maxRounds : 10);

        if (maxRetries > 1) {
            builder.retryExecutor(new RetrySupport.RetryExecutor(
                    new RetrySupport.RetryPolicy(maxRetries, Duration.ofMillis(80), 2.0d)));
        }

        if (enableCompression) {
            builder.contextCompressionManager(new ContextCompressionManager(
                    com.agent.foundation.compression.ContextPolicy.defaults()));
        }

        return builder.build();
    }
}

