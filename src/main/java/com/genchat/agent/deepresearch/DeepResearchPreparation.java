package com.genchat.agent.deepresearch;

import com.agent.foundation.model.RunnableParams;
import com.genchat.application.stream.PersistentChatMemoryLoader;
import com.genchat.common.prompts.PlanExecutePrompts;
import com.genchat.dto.AiChatSession;
import com.genchat.agent.deepresearch.model.OverAllState;
import com.genchat.foundation.FoundationReactAgentFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import reactor.core.publisher.Sinks;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Component
@RequiredArgsConstructor
public class DeepResearchPreparation {

    private final DeepResearchDependencies deps;

    public ChatMemory buildChatMemory(String conversationId) {
        return PersistentChatMemoryLoader.load(deps.sessionService(), conversationId);
    }

    public OverAllState initStateAndSaveQuestion(DeepResearchRunContext ctx,
                                                 String conversationsId,
                                                 String question,
                                                 ChatMemory chatMemory) {
        var overAllState = new OverAllState(conversationsId, question);
        var historyMessages = chatMemory.get(conversationsId);
        if (!CollectionUtils.isEmpty(historyMessages)) {
            historyMessages.forEach(overAllState::add);
        }
        overAllState.add(new UserMessage(question));
        var aiChatSession = deps.sessionService().saveQuestion(
                AiChatSession.builder()
                        .question(question)
                        .sessionId(conversationsId)
                        .build()
        );
        ctx.setCurrentSessionId(aiChatSession.getId());
        return overAllState;
    }

    public void clarifyRequirement(DeepResearchRunContext ctx,
                                   OverAllState overAllState,
                                   Sinks.Many<String> sink,
                                   AtomicBoolean finished,
                                   Runnable onComplete) {
        DeepResearchStreams.emitThinking(sink, finished, "\n🔍Your needs are being analyzed...\n");
        var messages = new ArrayList<Message>();
        messages.add(new SystemMessage(PlanExecutePrompts.getCurrentTime()
                + "\n\n" + PlanExecutePrompts.REQUIREMENT_CLARIFICATION));
        messages.addAll(overAllState.getMessages());

        var responseBuffer = new StringBuilder();
        var agent = FoundationReactAgentFactory.create(
                deps.chatModel(),
                deps.tools(),
                deps.maxRounds(),
                0,
                true);
        var disposable = agent.streamForEvents(
                        messages,
                        RunnableParams.builder().conversationId(overAllState.getConversationId()).build())
                .doOnNext(chunk -> {
                    if (chunk == null) {
                        return;
                    }
                    if (chunk.type() == com.agent.foundation.model.AgentStreamEvent.Type.REASONING_CONTENT) {
                        DeepResearchStreams.emitThinking(sink, finished, chunk.content());
                        return;
                    }
                    if (chunk.type() == com.agent.foundation.model.AgentStreamEvent.Type.RETRY
                            || chunk.type() == com.agent.foundation.model.AgentStreamEvent.Type.PAUSED) {
                        DeepResearchStreams.emitThinking(sink, finished, chunk.content() + "\n");
                        return;
                    }
                    if (chunk.type() == com.agent.foundation.model.AgentStreamEvent.Type.COMPLETED) {
                        responseBuffer.append(chunk.content());
                    }
                })
                .doOnComplete(() -> handleClarificationComplete(sink, responseBuffer, finished, onComplete))
                .doOnError(throwable -> {
                    log.error("Clarify requirements error, please try again later");
                    if (finished.compareAndSet(false, true)) {
                        sink.tryEmitError(throwable);
                    }
                })
                .subscribe();
        ctx.getCompositeDisposable().add(disposable);
    }

    public void generateResearchTopicPhase(DeepResearchRunContext ctx,
                                           OverAllState overAllState,
                                           Sinks.Many<String> sink,
                                           AtomicBoolean finished,
                                           Runnable onComplete) {
        DeepResearchStreams.emitThinking(sink, finished, "📝Research topics are being generated\n");
        var messages = new ArrayList<Message>();
        messages.add(new SystemMessage(PlanExecutePrompts.getCurrentTime()
                + "\n\n" + PlanExecutePrompts.RESEARCH_TOPIC_GENERATION));
        if (!CollectionUtils.isEmpty(overAllState.getMessages())) {
            messages.addAll(overAllState.getMessages());
        }
        messages.add(new UserMessage("<original_question>" + overAllState.getQuestion() + "</original_question>"));
        var topicBuffer = new StringBuilder();

        var agent = FoundationReactAgentFactory.create(
                deps.chatModel(),
                deps.tools(),
                deps.maxRounds(),
                0,
                true);
        var disposable = agent.streamForEvents(
                        messages,
                        RunnableParams.builder().conversationId(overAllState.getConversationId()).build())
                .doOnNext(chunk -> {
                    if (chunk == null) {
                        return;
                    }
                    if (chunk.type() == com.agent.foundation.model.AgentStreamEvent.Type.REASONING_CONTENT) {
                        DeepResearchStreams.emitThinking(sink, finished, chunk.content());
                        return;
                    }
                    if (chunk.type() == com.agent.foundation.model.AgentStreamEvent.Type.RETRY
                            || chunk.type() == com.agent.foundation.model.AgentStreamEvent.Type.PAUSED) {
                        DeepResearchStreams.emitThinking(sink, finished, chunk.content() + "\n");
                        return;
                    }
                    if (chunk.type() == com.agent.foundation.model.AgentStreamEvent.Type.COMPLETED) {
                        topicBuffer.append(chunk.content());
                    }
                })
                .doOnComplete(() -> {
                    overAllState.setRefinedResearchTopic(topicBuffer.toString());
                    DeepResearchStreams.emitThinking(sink, finished, "\n✅ The research topic has been generated\n\n");
                    onComplete.run();
                })
                .doOnError(throwable -> {
                    log.error("Research topic generation failed", throwable);
                    if (finished.compareAndSet(false, true)) {
                        sink.tryEmitError(throwable);
                    }
                })
                .subscribe();
        ctx.getCompositeDisposable().add(disposable);
    }

    private void handleClarificationComplete(Sinks.Many<String> sink,
                                             StringBuilder responseBuffer,
                                             AtomicBoolean finished,
                                             Runnable onComplete) {
        var response = responseBuffer.toString();
        DeepResearchStreams.emitThinking(sink, finished, "\n✅Requirements analysis completed\n");
        boolean needsMoreInfo = response.contains("【Additional information is needed】");
        if (needsMoreInfo) {
            String pauseMessage = "⏸【Pause for in-depth research】" + response.replace("【Additional information is needed】", "").trim();
            sink.tryEmitNext(new com.genchat.common.AgentStreamEvent.Text(pauseMessage).toJSON());
            if (finished.compareAndSet(false, true)) {
                sink.tryEmitComplete();
            }
            return;
        }
        DeepResearchStreams.emitThinking(sink, finished, "✅ Sufficient information and ready to generate a research topic\n");
        onComplete.run();
    }

}
