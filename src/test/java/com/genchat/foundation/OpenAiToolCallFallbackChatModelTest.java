package com.genchat.foundation;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.tool.ToolCallback;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class OpenAiToolCallFallbackChatModelTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(5);
    private final ChatModel delegate = mock(ChatModel.class);
    private final OpenAiToolCallFallbackChatModel model = new OpenAiToolCallFallbackChatModel(delegate);
    private final Prompt prompt = new Prompt("hello");

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void toolRequestsBypassStreamingLazilyOnABlockingSafeThread(boolean providerOptions) {
        var callback = mock(ToolCallback.class);
        ChatOptions options = providerOptions
                ? OpenAiChatOptions.builder().model("test-model").parallelToolCalls(false).toolCallbacks(callback).build()
                : ToolCallingChatOptions.builder().toolCallbacks(callback).build();
        var toolPrompt = new Prompt("hello", options);
        var response = response(AssistantMessage.builder().toolCalls(List.of(
                new AssistantMessage.ToolCall("call_1", "function", "lookup", "{}"))).build());
        when(delegate.call(toolPrompt)).thenAnswer(invocation -> {
            assertSame(toolPrompt, invocation.getArgument(0));
            assertFalse(Schedulers.isInNonBlockingThread());
            return response;
        });

        var stream = model.stream(toolPrompt);
        verifyNoInteractions(delegate);
        assertEquals(List.of(response), stream.subscribeOn(Schedulers.parallel()).collectList().block(TIMEOUT));
        assertEquals(List.of(response), stream.collectList().block(TIMEOUT));
        verify(delegate, times(2)).call(toolPrompt);
        verify(delegate, never()).stream(toolPrompt);
        verifyNoInteractions(callback);
    }

    @Test
    void inheritsDefaultToolsWhenRequestOptionsAreAbsent() {
        var options = OpenAiChatOptions.builder().parallelToolCalls(false).toolCallbacks(mock(ToolCallback.class)).build();
        var response = response(new AssistantMessage("ok"));
        when(delegate.getOptions()).thenReturn(options);
        when(delegate.call(prompt)).thenReturn(response);

        assertEquals(List.of(response), model.stream(prompt).collectList().block(TIMEOUT));
        verify(delegate, times(1)).call(prompt);
        verify(delegate, never()).stream(prompt);
    }

    @Test
    void explicitToolFreeOptionsOverrideDefaultToolsAndKeepStreaming() {
        when(delegate.getOptions()).thenReturn(
                OpenAiChatOptions.builder().toolCallbacks(mock(ToolCallback.class)).build());
        var toolFreePrompt = new Prompt("hello", OpenAiChatOptions.builder().toolCallbacks(List.of()).build());
        var first = response(new AssistantMessage("first"));
        var second = response(new AssistantMessage("second"));
        when(delegate.stream(toolFreePrompt)).thenReturn(Flux.just(first, second));

        assertEquals(List.of(first, second), model.stream(toolFreePrompt).collectList().block(TIMEOUT));
        verify(delegate, never()).call(toolFreePrompt);
    }

    @Test
    void toolRequestFailureIsPropagatedWithoutStreamingOrResubmission() {
        var toolPrompt = new Prompt("hello",
                OpenAiChatOptions.builder().parallelToolCalls(false).toolCallbacks(mock(ToolCallback.class)).build());
        var failure = new IllegalStateException("provider failure");
        when(delegate.call(toolPrompt)).thenThrow(failure);

        assertSame(failure, assertThrows(IllegalStateException.class,
                () -> model.stream(toolPrompt).blockLast(TIMEOUT)));
        verify(delegate, times(1)).call(toolPrompt);
        verify(delegate, never()).stream(toolPrompt);
    }

    @Test
    void normalStreamAndOptionsAreUnchanged() {
        var options = OpenAiChatOptions.builder().model("test-model").build();
        var response = response(new AssistantMessage("ok"));
        when(delegate.getOptions()).thenReturn(options);
        when(delegate.stream(prompt)).thenReturn(Flux.just(response));

        assertSame(options, model.getOptions());
        assertEquals(List.of(response), model.stream(prompt).collectList().block(TIMEOUT));
        verify(delegate, never()).call(prompt);
    }

    @Test
    void fallbackIsLazyAndRunsOffTheNonBlockingThread() {
        var response = response(new AssistantMessage("recovered"));
        when(delegate.stream(prompt)).thenReturn(Flux.error(mergerFailure()));
        when(delegate.call(prompt)).thenAnswer(invocation -> {
            assertFalse(Schedulers.isInNonBlockingThread());
            return response;
        });

        var stream = model.stream(prompt);
        verifyNoInteractions(delegate);
        assertEquals(List.of(response), stream.subscribeOn(Schedulers.parallel()).collectList().block(TIMEOUT));
        verify(delegate, times(1)).call(prompt);
    }

    @ParameterizedTest
    @ValueSource(strings = {"text", "think", "tool", "reasoning", "refusal"})
    void doesNotRestartAfterOutput(String kind) {
        var message = switch (kind) {
            case "tool" -> AssistantMessage.builder().toolCalls(List.of(
                    new AssistantMessage.ToolCall("call_1", "function", "lookup", "{}"))).build();
            case "reasoning" -> AssistantMessage.builder().properties(Map.of("reasoningContent", "thinking")).build();
            case "refusal" -> AssistantMessage.builder().properties(Map.of("refusal", "refused")).build();
            case "think" -> new AssistantMessage("<think>thinking");
            default -> new AssistantMessage("partial answer");
        };
        var failure = mergerFailure();
        when(delegate.stream(prompt)).thenReturn(Flux.concat(Flux.just(response(message)), Flux.error(failure)));

        assertSame(failure, assertThrows(NoSuchElementException.class,
                () -> model.stream(prompt).blockLast(TIMEOUT)));
        verify(delegate, never()).call(prompt);
    }

    @Test
    void unrelatedOptionalFailureIsNotRetried() {
        var failure = new NoSuchElementException("unrelated");
        when(delegate.stream(prompt)).thenReturn(Flux.error(failure));

        assertSame(failure, assertThrows(NoSuchElementException.class,
                () -> model.stream(prompt).blockLast(TIMEOUT)));
        verify(delegate, never()).call(prompt);
    }

    @Test
    void otherStreamingErrorsAreNotRetried() {
        var failure = new IllegalStateException("provider failure");
        when(delegate.stream(prompt)).thenReturn(Flux.error(failure));

        assertSame(failure, assertThrows(IllegalStateException.class,
                () -> model.stream(prompt).blockLast(TIMEOUT)));
        verify(delegate, never()).call(prompt);
    }

    @Test
    void failedFallbackIsPropagatedWithoutAnotherAttempt() {
        var failure = new IllegalStateException("sync provider failure");
        when(delegate.stream(prompt)).thenReturn(Flux.error(mergerFailure()));
        when(delegate.call(prompt)).thenThrow(failure);

        assertSame(failure, assertThrows(IllegalStateException.class,
                () -> model.stream(prompt).blockLast(TIMEOUT)));
        verify(delegate, times(1)).call(prompt);
    }

    @Test
    void emptyFallbackDoesNotHideTheOriginalFailure() {
        var failure = mergerFailure();
        when(delegate.stream(prompt)).thenReturn(Flux.error(failure));
        when(delegate.call(prompt)).thenReturn(new ChatResponse(List.of()));

        assertSame(failure, assertThrows(NoSuchElementException.class,
                () -> model.stream(prompt).blockLast(TIMEOUT)));
        verify(delegate, times(1)).call(prompt);
    }

    @Test
    void outputTrackingIsIsolatedPerSubscription() {
        var subscriptions = new AtomicInteger();
        var response = response(new AssistantMessage("ok"));
        var failure = mergerFailure();
        when(delegate.stream(prompt)).thenReturn(Flux.defer(() -> subscriptions.getAndIncrement() == 0
                ? Flux.concat(Flux.just(response), Flux.error(failure)) : Flux.error(failure)));
        when(delegate.call(prompt)).thenReturn(response);
        var stream = model.stream(prompt);

        assertSame(failure, assertThrows(NoSuchElementException.class, () -> stream.blockLast(TIMEOUT)));
        assertEquals(List.of(response), stream.collectList().block(TIMEOUT));
        verify(delegate, times(1)).call(prompt);
    }

    private static ChatResponse response(AssistantMessage message) {
        return new ChatResponse(List.of(new Generation(message)));
    }

    private static NoSuchElementException mergerFailure() {
        var error = new NoSuchElementException("No value present");
        error.setStackTrace(new StackTraceElement[]{new StackTraceElement(
                "org.springframework.ai.openai.OpenAiChatModel$ChunkMerger",
                "lambda$chunkToChatCompletion$6", "OpenAiChatModel.java", 1130)});
        return error;
    }
}
