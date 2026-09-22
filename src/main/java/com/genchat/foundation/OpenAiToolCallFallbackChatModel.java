package com.genchat.foundation;

import java.util.Arrays;
import java.util.NoSuchElementException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.openai.OpenAiChatOptions;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Temporary compatibility boundary for Spring AI 2.0.0's OpenAI tool chunk merger.
 * Tool-bearing requests bypass the merger using a synchronous provider call, exposed
 * as a Flux on a blocking-safe scheduler. Their output is delivered per response,
 * not per token; tool-free requests retain streaming. Waiting for a merger failure
 * is insufficient because text or reasoning may already have been delivered.
 */
final class OpenAiToolCallFallbackChatModel implements ChatModel {

    private static final Logger log = LoggerFactory.getLogger(OpenAiToolCallFallbackChatModel.class);
    private static final String CHUNK_MERGER = "org.springframework.ai.openai.OpenAiChatModel$ChunkMerger";

    private final ChatModel delegate;

    OpenAiToolCallFallbackChatModel(ChatModel delegate) {
        this.delegate = delegate;
    }

    @Override
    public ChatOptions getOptions() {
        return delegate.getOptions();
    }

    @Override
    public ChatResponse call(Prompt prompt) {
        return delegate.call(singleToolPrompt(prompt));
    }

    @Override
    public Flux<ChatResponse> stream(Prompt prompt) {
        return Flux.defer(() -> {
            Prompt request = singleToolPrompt(prompt);
            // Spring AI 2.0 uses explicit request options as-is; model defaults
            // apply only when no request options were supplied.
            ChatOptions options = request.getOptions() != null ? request.getOptions() : delegate.getOptions();
            if (options instanceof ToolCallingChatOptions toolOptions
                    && toolOptions.getToolCallbacks() != null
                    && !toolOptions.getToolCallbacks().isEmpty()) {
                log.debug("Using non-streaming OpenAI request to bypass Spring AI 2.0.0 tool chunk merger");
                // Preserve provider options and original schemas, except the single-call constraint.
                // Foundation still owns tool execution; only model transport changes.
                return Mono.fromCallable(() -> delegate.call(request))
                        .subscribeOn(Schedulers.boundedElastic())
                        .flux();
            }

            var outputDelivered = new AtomicBoolean();
            return delegate.stream(request)
                    .doOnNext(response -> {
                        if (hasOutput(response)) {
                            outputDelivered.set(true);
                        }
                    })
                    .onErrorResume(error -> {
                        if (outputDelivered.get() || !isChunkMergerFailure(error)) {
                            return Flux.error(error);
                        }
                        log.warn("OpenAI streaming tool-call merge failed before output; retrying once without streaming");
                        // Reuse the exact prompt: provider options and original tool schemas
                        // must not be replaced by the foundation's synthetic callbacks.
                        return Mono.fromCallable(() -> delegate.call(request))
                                .filter(OpenAiToolCallFallbackChatModel::hasOutput)
                                .switchIfEmpty(Mono.error(error))
                                .subscribeOn(Schedulers.boundedElastic());
                    });
        });
    }

    private Prompt singleToolPrompt(Prompt prompt) {
        ChatOptions options = prompt.getOptions() != null ? prompt.getOptions() : delegate.getOptions();
        if (options instanceof OpenAiChatOptions openAiOptions
                && openAiOptions.getToolCallbacks() != null
                && !openAiOptions.getToolCallbacks().isEmpty()
                && !Boolean.FALSE.equals(openAiOptions.getParallelToolCalls())) {
            // Foundation accepts one invocation per reasoning decision. Do not mutate shared defaults.
            return new Prompt(prompt.getInstructions(), openAiOptions.mutate().parallelToolCalls(false).build());
        }
        return prompt;
    }

    private static boolean hasOutput(ChatResponse response) {
        return response.getResults().stream().anyMatch(generation -> {
            var output = generation.getOutput();
            return output != null && (output.hasToolCalls()
                    || !output.getMedia().isEmpty()
                    || (output.getText() != null && !output.getText().isEmpty())
                    || hasText(output.getMetadata().get("reasoningContent"))
                    || hasText(output.getMetadata().get("refusal")));
        });
    }

    private static boolean hasText(Object value) {
        return value instanceof String text && !text.isEmpty();
    }

    private static boolean isChunkMergerFailure(Throwable error) {
        return error instanceof NoSuchElementException
                && Arrays.stream(error.getStackTrace()).anyMatch(frame -> CHUNK_MERGER.equals(frame.getClassName()));
    }
}

