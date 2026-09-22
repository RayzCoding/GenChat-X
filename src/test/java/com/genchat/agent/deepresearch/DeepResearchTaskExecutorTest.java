package com.genchat.agent.deepresearch;

import com.genchat.agent.SimpleReactAgent;
import com.genchat.application.agent.AgentFactory;
import com.genchat.config.GenChatProperties;
import com.genchat.config.WebSearchToolInitConfig;
import com.genchat.dto.PlanTask;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import reactor.core.Disposables;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DeepResearchTaskExecutorTest {

    @ParameterizedTest
    @CsvSource({"5,false", "6,true", "15,true"})
    void taskBudgetIncludesSearchesAndFinalAnswer(int budget, boolean succeeds) {
        var calls = new AtomicInteger();
        var searches = new AtomicInteger();
        ToolCallback tool = FunctionToolCallback.builder("tavily_search", (Query query) -> {
                    searches.incrementAndGet();
                    return "{\"results\":[]}";
                })
                .description("Search the web")
                .inputType(Query.class)
                .build();
        var tools = List.of(tool);
        var model = mock(ChatModel.class);
        when(model.stream(any(Prompt.class))).thenAnswer(invocation -> {
            Prompt prompt = invocation.getArgument(0);
            assertTrue(prompt.getInstructions().stream().anyMatch(message -> message.getText()
                    .contains("at most " + budget + " model responses")));
            int round = calls.incrementAndGet();
            AssistantMessage message = round <= 5
                    ? AssistantMessage.builder().toolCalls(List.of(new AssistantMessage.ToolCall(
                            "call_" + round, "function", "tavily_search", "{\"query\":\"search " + round + "\"}"))).build()
                    : new AssistantMessage("Verified findings; unavailable figures are explicitly marked as missing.");
            return Flux.just(new ChatResponse(List.of(new Generation(message))));
        });
        var factory = mock(AgentFactory.class);
        when(factory.createSimpleReactAgent(tools)).thenReturn(new SimpleReactAgent(model, tools));
        var deps = new DeepResearchDependencies(null, model, tools, null, 1, budget, 1);
        var context = new DeepResearchRunContext(deps);
        context.setCompositeDisposable(Disposables.composite());
        var executor = new DeepResearchTaskExecutor(deps, factory);

        var result = executor.executeWithRetry(context, new PlanTask("task-1", "Retrieve evidence", 1),
                "None", Sinks.many().unicast().onBackpressureBuffer(), new AtomicBoolean());

        assertEquals(succeeds, result.success());
        assertEquals(succeeds ? 6 : 5, calls.get());
        assertEquals(5, searches.get());
        if (succeeds) {
            assertTrue(result.output().startsWith("Verified findings"));
        } else {
            assertTrue(result.error().contains("exhausted 5 reasoning round(s)"));
        }
    }

    @Test
    void configurationWiresIndependentTaskBudget() {
        var properties = new GenChatProperties();
        properties.getDeepResearch().setTaskMaxRounds(20);
        var tools = mock(WebSearchToolInitConfig.class);
        when(tools.getWebSearchToolCallbacks()).thenReturn(new ToolCallback[0]);

        var deps = new DeepResearchConfiguration().deepResearchDependencies(
                null, mock(ChatModel.class), tools, null, properties);

        assertEquals(20, deps.taskMaxRounds());
        assertEquals(1, deps.maxRounds());
        assertEquals(1, deps.toolSemaphore().availablePermits());
    }

    public record Query(String query) {
    }
}
