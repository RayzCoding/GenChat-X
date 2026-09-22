package com.genchat.agent.core;

import com.agent.foundation.core.ReactAgent;
import com.agent.foundation.model.RunnableParams;
import com.genchat.application.agent.PersistentChatAgent;
import com.genchat.application.audit.AgentAuditSupport;
import com.genchat.application.stream.AgentStreamLifecycle;
import com.genchat.application.stream.PersistentChatMemoryLoader;
import com.genchat.common.AgentStreamEvent;
import com.genchat.common.ToolRecord;
import com.genchat.common.utils.JacksonJson;
import com.genchat.dto.AiChatSession;
import com.genchat.agent.model.AgentState;
import com.genchat.context.ContextCompactor;
import com.genchat.foundation.FoundationReactAgentFactory;
import com.genchat.foundation.FoundationToolPayloads;
import com.genchat.service.AgentTaskService;
import com.genchat.service.AiChatSessionService;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.*;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Shared ReAct loop engine for conversation agents with persistent memory.
 */
@Slf4j
@Setter
public abstract class AbstractReactAgent implements PersistentChatAgent {

    protected final ChatModel chatModel;
    protected final AiChatSessionService sessionService;
    protected final AgentTaskService agentTaskService;
    protected final List<ToolCallback> tools;
    protected ChatMemory chatMemory;
    protected final String systemPrompt = "";
    protected int maxRounds;
    protected int maxRetries;
    protected Long currentSessionId;
    protected String currentQuestion;
    protected String currentRecommendations;
    protected long firstResponseTime;
    protected long startTime;
    protected boolean enableRecommendations = true;
    protected ContextCompactor contextCompactor;
    protected final List<ToolRecord> toolRecords = Collections.synchronizedList(new ArrayList<>());

    protected AbstractReactAgent(ChatModel chatModel,
                                 AiChatSessionService sessionService,
                                 AgentTaskService agentTaskService,
                                 List<ToolCallback> tools) {
        this.chatModel = chatModel;
        this.sessionService = sessionService;
        this.agentTaskService = agentTaskService;
        this.tools = tools;
    }

    protected abstract String getAgentType();

    protected abstract String getPrimarySystemPrompt();

    protected abstract AiChatSession buildSessionForSave(ReactStreamRequest request);

    protected void appendExtraUserMessages(List<Message> messages, ReactStreamRequest request) {
    }

    protected AgentState createAgentState() {
        return new AgentState();
    }

    protected void emitToolThinking(String toolName, String argsJson, Sinks.Many<String> sink) {
    }

    protected void handleToolResult(String toolName, String result, AgentState agentState) {
        if (toolName.contains("tavily") && agentState != null) {
            ReactToolSupport.parseTavilySearchResult(result, agentState);
        }
    }

    protected void onNonToolFinish(String finalText,
                                   Sinks.Many<String> sink,
                                   String conversationId,
                                   AgentState agentState) {
        if (agentState != null && !agentState.searchResults.isEmpty()) {
            sink.tryEmitNext(AgentStreamEvent.Reference.of(JacksonJson.toJson(agentState.searchResults)).toJSON());
        }
        if (enableRecommendations) {
            String recommendations = generateRecommendations(conversationId, currentQuestion, finalText);
            if (recommendations != null) {
                currentRecommendations = recommendations;
                sink.tryEmitNext(AgentStreamEvent.Recommend.of(recommendations).toJSON());
            }
        }
    }

    protected void onForceFinalComplete(String finalText,
                                        Sinks.Many<String> sink,
                                        String conversationId,
                                        AgentState agentState) {
        onNonToolFinish(finalText, sink, conversationId, agentState);
    }

    protected String getForceFinalSystemPrompt() {
        return getPrimarySystemPrompt();
    }

    protected Flux<String> streamInternal(ReactStreamRequest request) {
        String conversationId = request.conversationId();
        String question = request.question();

        if (AgentStreamLifecycle.isConversationBusy(agentTaskService, conversationId)) {
            return AgentStreamLifecycle.conversationBusyError();
        }
        initTimers();
        toolRecords.clear();

        var started = AgentStreamLifecycle.startStream(agentTaskService, conversationId, getAgentType());
        if (started == null) {
            return AgentStreamLifecycle.conversationBusyError();
        }
        Sinks.Many<String> sink = started.sink();

        var aiChatSession = sessionService.saveQuestion(buildSessionForSave(request));
        currentSessionId = aiChatSession.getId();

        List<Message> messages = Collections.synchronizedList(new ArrayList<>());
        messages.add(new SystemMessage(getPrimarySystemPrompt()));
        if (StringUtils.hasLength(systemPrompt)) {
            messages.add(new SystemMessage(systemPrompt));
        }
        loadChatHistory(conversationId, messages, true, true);

        messages.add(new UserMessage("<question>" + question + "</question>"));
        appendExtraUserMessages(messages, request);

        currentQuestion = question;

        var hasSentFinalResult = new AtomicBoolean(false);
        var hasEmittedTextDelta = new AtomicBoolean(false);
        var buffers = AgentStreamLifecycle.StreamBuffers.create();
        var agentState = createAgentState();

        ReactAgent foundationAgent = FoundationReactAgentFactory.create(
                chatModel,
                tools,
                maxRounds,
                maxRetries,
                contextCompactor != null);

        foundationAgent.streamForEvents(
                        messages,
                        RunnableParams.builder().conversationId(conversationId).build())
                .publishOn(Schedulers.boundedElastic())
                .subscribe(event -> handleFoundationEvent(
                        event,
                        sink,
                        hasSentFinalResult,
                        hasEmittedTextDelta,
                        conversationId,
                        agentState), error -> {
                    log.error("agent.stream.failed conversationId={}", conversationId, error);
                    if (!hasSentFinalResult.get()) {
                        hasSentFinalResult.set(true);
                        sink.tryEmitNext(AgentStreamEvent.Error.simple(error.getMessage()).toJSON());
                        sink.tryEmitNext(new AgentStreamEvent.Complete().toJSON());
                        sink.tryEmitComplete();
                    }
                }, () -> {
                    if (!hasSentFinalResult.get()) {
                        hasSentFinalResult.set(true);
                        sink.tryEmitNext(new AgentStreamEvent.Complete().toJSON());
                        sink.tryEmitComplete();
                    }
                });

        return AgentStreamLifecycle.attach(
                started.flux(),
                conversationId,
                agentTaskService,
                buffers,
                this::recordFirstResponse,
                () -> hasSentFinalResult.set(true),
                () -> {
                    long totalResponseTime = getTotalResponseTime();
                    AgentAuditSupport.persistSession(
                            sessionService,
                            currentSessionId,
                            buffers,
                            agentState,
                            totalResponseTime,
                            firstResponseTime,
                            JacksonJson.toJson(toolRecords),
                            currentRecommendations,
                            getAgentType(),
                            null);
                    AgentAuditSupport.logCompletion(
                            log,
                            getAgentType(),
                            conversationId,
                            totalResponseTime,
                            firstResponseTime);
                });
    }

    public void initPersistentChatMemory(String conversationId, int maxMessages) {
        this.chatMemory = PersistentChatMemoryLoader.load(sessionService, conversationId, maxMessages);
    }

    protected void handleFoundationEvent(com.agent.foundation.model.AgentStreamEvent event,
                                         Sinks.Many<String> sink,
                                         AtomicBoolean hasSentFinalResult,
                                         AtomicBoolean hasEmittedTextDelta,
                                         String conversationId,
                                         AgentState agentState) {
        if (event == null || hasSentFinalResult.get()) {
            return;
        }

        if (event.type() != com.agent.foundation.model.AgentStreamEvent.Type.STARTED) {
            recordFirstResponse();
        }

        switch (event.type()) {
            case STARTED -> {
                // Keep GenChat stream contract; started is represented by first thinking/text event.
            }
            case REASONING -> {
                if (StringUtils.hasLength(event.content())) {
                    sink.tryEmitNext(new AgentStreamEvent.Thinking(event.content() + "\n").toJSON());
                }
            }
            case REASONING_CONTENT -> {
                if (StringUtils.hasLength(event.content())) {
                    sink.tryEmitNext(new AgentStreamEvent.Thinking(event.content() + "\n").toJSON());
                }
            }
            case TEXT_DELTA -> {
                if (StringUtils.hasLength(event.content())) {
                    hasEmittedTextDelta.set(true);
                    sink.tryEmitNext(new AgentStreamEvent.Text(event.content()).toJSON());
                }
            }
            case REASONING_DELTA -> {
                if (StringUtils.hasLength(event.content())) {
                    sink.tryEmitNext(new AgentStreamEvent.Thinking(event.content()).toJSON());
                }
            }
            case RETRY, PAUSED -> sink.tryEmitNext(new AgentStreamEvent.Thinking(event.content() + "\n").toJSON());
            case TOOL_EXECUTED -> {
                var payload = FoundationToolPayloads.decode(event.content());
                String toolName = payload.toolName();
                String toolCallId = payload.toolCallId();
                String argumentsJson = payload.argumentsJson();
                String result = payload.result();
                log.info("agent.tool.event conversationId={} toolName={} toolCallId={} argsLength={} resultLength={}",
                        conversationId,
                        toolName,
                        toolCallId,
                        argumentsJson == null ? 0 : argumentsJson.length(),
                        result == null ? 0 : result.length());
                sink.tryEmitNext(new AgentStreamEvent.ToolStart(toolName, toolCallId, argumentsJson).toJSON());
                sink.tryEmitNext(new AgentStreamEvent.ToolEnd(toolName, toolCallId, result).toJSON());
                toolRecords.add(new ToolRecord(toolName, toolCallId, argumentsJson, result));
                handleToolResult(toolName, result, agentState);
            }
            case ERROR -> {
                hasSentFinalResult.set(true);
                sink.tryEmitNext(AgentStreamEvent.Error.simple(StringUtils.hasLength(event.content())
                        ? event.content()
                        : "Agent execution failed").toJSON());
                sink.tryEmitNext(new AgentStreamEvent.Complete().toJSON());
                sink.tryEmitComplete();
            }
            case COMPLETED -> {
                hasSentFinalResult.set(true);
                var finalText = event.content();
                if (!hasEmittedTextDelta.get() && StringUtils.hasLength(finalText)) {
                    sink.tryEmitNext(new AgentStreamEvent.Text(finalText).toJSON());
                }
                onNonToolFinish(finalText, sink, conversationId, agentState);
                sink.tryEmitNext(new AgentStreamEvent.Complete().toJSON());
                sink.tryEmitComplete();
            }
            case STAGE -> {
                // Internal foundation events are not exposed as user-facing stream chunks.
            }
        }
    }

    private String generateRecommendations(String conversationId, String currentQuestion, String currentAnswer) {
        if (!enableRecommendations) {
            return null;
        }
        return ReactRecommendationService.generate(
                chatModel,
                buildHistoryMessages(conversationId, true, true),
                currentQuestion,
                currentAnswer);
    }

    protected String getHistoryLabel() {
        return "Conversation history：";
    }


    private void loadChatHistory(String conversationId, List<Message> messages, boolean skipSystem, boolean addLabel) {
        var historyMessages = buildHistoryMessages(conversationId, skipSystem, addLabel);
        if (!historyMessages.isEmpty()) {
            messages.addAll(historyMessages);
        }
    }

    private List<Message> buildHistoryMessages(String conversationId, boolean skipSystem, boolean addLabel) {
        var historyMessages = new ArrayList<Message>();
        if (!ObjectUtils.isEmpty(conversationId) && !ObjectUtils.isEmpty(chatMemory)) {
            var history = chatMemory.get(conversationId);
            if (!ObjectUtils.isEmpty(history)) {
                if (addLabel) {
                    historyMessages.add(new UserMessage(getHistoryLabel()));
                }
                for (Message msg : history) {
                    if (skipSystem && msg instanceof SystemMessage) {
                        continue;
                    }
                    historyMessages.add(msg);
                }
            }
        }
        return historyMessages;
    }


    protected void recordFirstResponse() {
        if (firstResponseTime == 0 && startTime > 0) {
            firstResponseTime = System.currentTimeMillis() - startTime;
            log.debug("Record first response time: {}ms", firstResponseTime);
        }
    }

    protected long getTotalResponseTime() {
        if (startTime == 0) {
            return 0;
        }
        return System.currentTimeMillis() - startTime;
    }

    protected void initTimers() {
        startTime = System.currentTimeMillis();
        firstResponseTime = 0;
    }
}
