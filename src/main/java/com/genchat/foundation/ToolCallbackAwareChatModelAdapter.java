package com.genchat.foundation;

import com.agent.foundation.core.ChatModelAdapter;
import com.agent.foundation.core.ReasoningDecision;
import com.agent.foundation.core.ReasoningStreamChunk;
import com.agent.foundation.core.SpringAiChatModelAdapter;
import com.agent.foundation.tool.ToolDefinition;
import java.util.List;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.tool.ToolCallback;
import reactor.core.publisher.Flux;

/**
 * GenChat-side adapter that reuses the foundation Spring AI adapter but lets streaming
 * use the original Spring AI {@link ToolCallback} objects directly.
 */
public final class ToolCallbackAwareChatModelAdapter implements ChatModelAdapter {

    private final SpringAiChatModelAdapter delegate;
    private final List<ToolCallback> toolCallbacks;

    public ToolCallbackAwareChatModelAdapter(ChatModel chatModel, List<ToolCallback> toolCallbacks) {
        this.delegate = new SpringAiChatModelAdapter(chatModel instanceof OpenAiChatModel
                ? new OpenAiToolCallFallbackChatModel(chatModel) : chatModel);
        this.toolCallbacks = toolCallbacks == null ? List.of() : List.copyOf(toolCallbacks);
    }

    @Override
    public ReasoningDecision reason(List<Message> messages, List<ToolDefinition> tools) {
        if (toolCallbacks.isEmpty()) {
            return delegate.reason(messages, tools);
        }
        return delegate.reason(messages, toolCallbacks.toArray(ToolCallback[]::new));
    }

    @Override
    public ReasoningDecision reasonFinal(List<Message> messages) {
        return delegate.reasonFinal(messages);
    }

    @Override
    public Flux<ReasoningStreamChunk> reasonFinalStream(List<Message> messages) {
        return delegate.reasonFinalStream(messages);
    }

    @Override
    public ReasoningDecision reasonStructured(List<Message> messages, String schema, boolean nativeOutput) {
        return delegate.reasonStructured(messages, schema, nativeOutput);
    }

    @Override
    public Flux<ReasoningStreamChunk> reasonStream(List<Message> messages, List<ToolDefinition> tools) {
        if (toolCallbacks.isEmpty()) {
            return delegate.reasonStream(messages, tools);
        }
        return delegate.reasonStream(messages, toolCallbacks.toArray(ToolCallback[]::new));
    }
}


