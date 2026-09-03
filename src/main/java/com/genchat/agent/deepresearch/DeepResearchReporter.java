package com.genchat.agent.deepresearch;

import com.agent.foundation.model.RunnableParams;
import com.genchat.common.utils.JacksonJson;
import com.genchat.common.AgentStreamEvent;
import com.genchat.common.prompts.PlanExecutePrompts;
import com.genchat.agent.deepresearch.model.OverAllState;
import com.genchat.foundation.FoundationReactAgentFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Component;
import reactor.core.Disposable;
import reactor.core.publisher.Sinks;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Component
@RequiredArgsConstructor
public class DeepResearchReporter {

    private final DeepResearchDependencies deps;

    public void summarizeStream(DeepResearchRunContext ctx,
                                OverAllState state,
                                Sinks.Many<String> sink,
                                AtomicBoolean finished,
                                StringBuilder finalAnswerBuffer) {
        DeepResearchStreams.emitThinking(sink, finished, "\n📝 Generating final research report...\n\n");
        String toolResults = state.extractToolResults();

        Prompt prompt = new Prompt(List.of(
                new SystemMessage(PlanExecutePrompts.getCurrentTime() + "\n\n" + PlanExecutePrompts.SUMMARIZE),
                new UserMessage("""
                                        【Original User Question】
                                        %s
                        
                                        【Research Topic】
                                        %s
                        
                                        【Tool Retrieval Results】
                                        %s
                        """.formatted(
                        state.getQuestion(),
                        state.getRefinedResearchTopic() != null ? state.getRefinedResearchTopic() : "Research topic not generated",
                        toolResults.isEmpty() ? "(No relevant results retrieved)" : toolResults
                ))
        ));

        var agent = FoundationReactAgentFactory.create(
                deps.chatModel(),
                deps.tools(),
                deps.maxRounds(),
                0,
                true);
        Disposable disposable = agent.streamForEvents(
                        renderPrompt(prompt),
                        RunnableParams.builder().conversationId(state.getConversationId()).build())
                .doOnNext(event -> {
                    if (ctx.isStopped(finished)) {
                        return;
                    }
                    if (event == null) {
                        return;
                    }
                    if (event.type() == com.agent.foundation.model.AgentStreamEvent.Type.REASONING_CONTENT) {
                        DeepResearchStreams.emitThinking(sink, finished, event.content());
                        return;
                    }
                    if (event.type() == com.agent.foundation.model.AgentStreamEvent.Type.RETRY
                            || event.type() == com.agent.foundation.model.AgentStreamEvent.Type.PAUSED) {
                        DeepResearchStreams.emitThinking(sink, finished, event.content() + "\n");
                        return;
                    }
                    if (event.type() == com.agent.foundation.model.AgentStreamEvent.Type.COMPLETED) {
                        finalAnswerBuffer.append(event.content());
                        if (finished.get()) {
                            return;
                        }
                        sink.tryEmitNext(new com.genchat.common.AgentStreamEvent.Text(event.content()).toJSON());
                    }
                })
                .doOnComplete(() -> {
                    if (!ctx.getAllReferences().isEmpty()) {
                        sink.tryEmitNext(AgentStreamEvent.Reference.of(JacksonJson.toJson(ctx.getAllReferences())).toJSON());
                    }
                    if (finished.compareAndSet(false, true)) {
                        sink.tryEmitComplete();
                    }
                })
                .doOnError(e -> {
                    if (!finished.compareAndSet(false, true)) {
                        return;
                    }
                    sink.tryEmitError(e);
                })
                .subscribe();

        ctx.getCompositeDisposable().add(disposable);
    }

    public void compressIfNeeded(DeepResearchRunContext ctx,
                                 OverAllState state,
                                 Sinks.Many<String> sink,
                                 AtomicBoolean hasSentFinal) {
        if (state.currentChars() < ctx.getContextCharLimit()) {
            return;
        }

        log.warn("===== Context too large, compressing, size is {} =====", state.currentChars());
        DeepResearchStreams.emitThinking(sink, hasSentFinal, "📦 Context too large, compressing...\n");

        if (ctx.isStopped(hasSentFinal)) {
            return;
        }

        Prompt prompt = new Prompt(List.of(
                new SystemMessage(PlanExecutePrompts.getCurrentTime() + "\n\n" + """
                        ## Maximum compression limit (mandatory)
                        - Total character count of your output (including tags, spaces, newlines)
                        must not exceed: %s
                        - This is a hard limit, not a suggestion
                        - Exceeding it is considered compression failure
                        
                        """.formatted(ctx.getContextCharLimit()) + PlanExecutePrompts.COMPRESS),
                new UserMessage(state.renderFullContext())
        ));

        var agent = FoundationReactAgentFactory.create(
                deps.chatModel(),
                deps.tools(),
                deps.maxRounds(),
                0,
                true);
        String snapshot = agent.callForResult(
                        renderPrompt(prompt),
                        RunnableParams.builder().conversationId(state.getConversationId()).build())
                .content();

        state.getMessages().clear();
        state.add(new SystemMessage("【Compressed Agent State】\n" + snapshot));
        log.warn("===== Context compression completed, size is {} =====", state.currentChars());
        DeepResearchStreams.emitThinking(sink, hasSentFinal, "✅ Context compression completed\n");
    }

    private String renderPrompt(Prompt prompt) {
        StringBuilder sb = new StringBuilder();
        for (var message : prompt.getInstructions()) {
            sb.append('[').append(message.getMessageType()).append("]\n")
                    .append(message.getText() == null ? "" : message.getText())
                    .append("\n\n");
        }
        return sb.toString();
    }
}
