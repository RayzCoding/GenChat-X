package com.genchat.agent.core;

import com.agent.foundation.model.AgentStreamEvent;
import com.genchat.agent.model.AgentState;
import com.genchat.common.ToolRecord;
import com.genchat.dto.AiChatSession;
import com.genchat.foundation.FoundationToolPayloads;
import com.genchat.service.AgentTaskService;
import com.genchat.service.AiChatSessionService;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatModel;
import reactor.core.publisher.Sinks;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class AbstractReactAgentEventMappingTest {

    @Test
    void toolExecutedEventEmitsToolEventsAndPersistsToolRecord() {
        var agent = new TestAgent();
        Sinks.Many<String> sink = Sinks.many().unicast().onBackpressureBuffer();
        var emitted = new ArrayList<String>();
        sink.asFlux().doOnNext(chunk -> emitted.add(chunk)).subscribe();

        var payload = FoundationToolPayloads.encode(
                "tavily_search",
                java.util.Map.of("query", "java"),
                "call-1",
                "[{\"text\":{\"results\":[{\"url\":\"https://example.com\",\"title\":\"Example\",\"content\":\"Body\"}]}}]");

        var agentState = new AgentState();
        agent.applyEvent(new AgentStreamEvent(AgentStreamEvent.Type.TOOL_EXECUTED, payload),
                sink,
                new AtomicBoolean(false),
                new AtomicBoolean(false),
                agentState);

        assertEquals(2, emitted.size());
        assertTrue(emitted.get(0).contains("\"type\":\"tool_start\""));
        assertTrue(emitted.get(0).contains("\"toolName\":\"tavily_search\""));
        assertTrue(emitted.get(1).contains("\"type\":\"tool_end\""));

        ToolRecord record = agent.records().getFirst();
        assertEquals("tavily_search", record.toolName());
        assertEquals("call-1", record.toolCallId());
        assertTrue(record.arguments().contains("query"));
        assertTrue(record.result().contains("results"));
        assertEquals(1, agentState.searchResults.size());
    }

    @Test
    void reasoningEventIsForwardedAsThinking() {
        var agent = new TestAgent();
        Sinks.Many<String> sink = Sinks.many().unicast().onBackpressureBuffer();
        var emitted = new ArrayList<String>();
        sink.asFlux().doOnNext(chunk -> emitted.add(chunk)).subscribe();

        var agentState = new AgentState();
        agent.applyEvent(new AgentStreamEvent(AgentStreamEvent.Type.REASONING, "planning..."),
                sink,
                new AtomicBoolean(false),
                new AtomicBoolean(false),
                agentState);
        assertEquals(1, emitted.size());
        assertTrue(emitted.get(0).contains("\"type\":\"thinking\""));
        assertTrue(emitted.get(0).contains("planning..."));
    }

    @Test
    void textDeltaEventIsForwardedAsTextChunk() {
        var agent = new TestAgent();
        Sinks.Many<String> sink = Sinks.many().unicast().onBackpressureBuffer();
        var emitted = new ArrayList<String>();
        sink.asFlux().doOnNext(emitted::add).subscribe();

        var agentState = new AgentState();
        agent.applyEvent(new AgentStreamEvent(AgentStreamEvent.Type.TEXT_DELTA, "hello"),
                sink,
                new AtomicBoolean(false),
                new AtomicBoolean(false),
                agentState);

        assertEquals(1, emitted.size());
        assertTrue(emitted.getFirst().contains("\"type\":\"text\""));
        assertTrue(emitted.getFirst().contains("hello"));
    }

    @Test
    void completedAfterTextDeltaDoesNotDuplicateFinalText() {
        var agent = new TestAgent();
        Sinks.Many<String> sink = Sinks.many().unicast().onBackpressureBuffer();
        var emitted = new ArrayList<String>();
        sink.asFlux().doOnNext(emitted::add).subscribe();

        var agentState = new AgentState();
        var hasSentFinalResult = new AtomicBoolean(false);
        var hasEmittedTextDelta = new AtomicBoolean(false);

        agent.applyEvent(new AgentStreamEvent(AgentStreamEvent.Type.TEXT_DELTA, "成都"),
                sink,
                hasSentFinalResult,
                hasEmittedTextDelta,
                agentState);
        agent.applyEvent(new AgentStreamEvent(AgentStreamEvent.Type.COMPLETED, "成都天气"),
                sink,
                hasSentFinalResult,
                hasEmittedTextDelta,
                agentState);

        assertEquals(2, emitted.size());
        assertTrue(emitted.get(0).contains("\"type\":\"text\""));
        assertTrue(emitted.get(0).contains("成都"));
        assertTrue(emitted.get(1).contains("\"type\":\"complete\""));
        assertFalse(emitted.get(1).contains("成都天气"));
    }


    private static final class TestAgent extends AbstractReactAgent {

        private TestAgent() {
            super(mock(ChatModel.class), mock(AiChatSessionService.class), mock(AgentTaskService.class), List.of());
        }

        @Override
        protected String getAgentType() {
            return "test-agent";
        }

        @Override
        protected String getPrimarySystemPrompt() {
            return "system";
        }

        @Override
        protected AiChatSession buildSessionForSave(ReactStreamRequest request) {
            return null;
        }

        @Override
        protected void appendExtraUserMessages(List<Message> messages, ReactStreamRequest request) {
            // no-op
        }

        void applyEvent(AgentStreamEvent event,
                        Sinks.Many<String> sink,
                        AtomicBoolean hasSentFinalResult,
                        AtomicBoolean hasEmittedTextDelta,
                        AgentState agentState) {
            handleFoundationEvent(event, sink, hasSentFinalResult, hasEmittedTextDelta, "c1", agentState);
        }

        List<ToolRecord> records() {
            return toolRecords;
        }
    }
}


