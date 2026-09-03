package com.genchat.application.strategy;

import com.agent.foundation.model.RunnableParams;
import com.genchat.common.AgentStreamEvent;
import com.genchat.common.prompts.PptBuilderPrompts;
import com.genchat.common.utils.JacksonJson;
import com.genchat.dto.AiPptInst;
import com.genchat.dto.PptSchema;
import com.genchat.entity.PptInstStatus;
import com.genchat.foundation.FoundationReactAgentFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Sinks;

@Slf4j
@Component
public class SuccessStrategy implements PptStateStrategy {
    private static final PptInstStatus TARGET_STATUS = PptInstStatus.SUCCESS;

    @Override
    public void execute(AiPptInst inst,
                        Sinks.Many<String> sink,
                        String question,
                        StringBuilder thinkingBuffer,
                        PptStateStrategyContext context) {
        var fileUrl = inst.getFileUrl();
        var pptSchema = JacksonJson.fromJson(inst.getPptSchema(), PptSchema.class);
        var size = pptSchema.getSlides().size();
        String prompt;

        if (context.isModifyMode()) {
            var modifyQuestion = context.getModifyQuestion();
            prompt = PptBuilderPrompts.getModifySummaryPrompt(modifyQuestion, fileUrl);
        } else {
            var requirement = inst.getRequirement();
            prompt = PptBuilderPrompts.getSummaryPrompt(requirement, fileUrl, size);
        }
        var llmResponse = new StringBuilder();
        var agent = FoundationReactAgentFactory.create(
                context.getDependencies().chatModel(),
                context.getTools(),
                5,
                0,
                true);
        var disposable = agent.streamForEvents(
                        prompt,
                        RunnableParams.builder().conversationId(inst.getConversationId()).build())
                .doOnNext(chunk -> {
                    if (chunk == null) {
                        return;
                    }
                    if (chunk.type() == com.agent.foundation.model.AgentStreamEvent.Type.REASONING_CONTENT) {
                        sink.tryEmitNext(new AgentStreamEvent.Thinking(chunk.content()).toJSON());
                        return;
                    }
                    if (chunk.type() == com.agent.foundation.model.AgentStreamEvent.Type.RETRY
                            || chunk.type() == com.agent.foundation.model.AgentStreamEvent.Type.PAUSED) {
                        sink.tryEmitNext(new AgentStreamEvent.Thinking(chunk.content() + "\n").toJSON());
                        return;
                    }
                    if (chunk.type() == com.agent.foundation.model.AgentStreamEvent.Type.COMPLETED) {
                        sink.tryEmitNext(new AgentStreamEvent.Text(chunk.content()).toJSON());
                        llmResponse.append(chunk.content());
                    }
                })
                .doOnComplete(() -> {
                    updateSession(thinkingBuffer, context, llmResponse);
                    sink.tryEmitComplete();
                    context.setModifyMode(false);
                    context.setModifyQuestion(null);
                })
                .doOnError(err -> {
                    log.info("Summary generation failed", err);
                    sink.tryEmitError(err);
                    context.setModifyMode(false);
                    context.setModifyQuestion(null);
                })
                .subscribe();
        context.setDisposable(inst.getConversationId(), disposable);
    }

    private static void updateSession(StringBuilder thinkingBuffer, PptStateStrategyContext context, StringBuilder llmResponse) {
        var aiChatSessionOptional = context.getDependencies().sessionService().queryById(context.getCurrentSessionId());
        if (aiChatSessionOptional.isEmpty()) {
            log.error("Session id {} not found", context.getCurrentSessionId());
            return;
        }
        var aiChatSession = aiChatSessionOptional.get();
        aiChatSession.setAnswer(llmResponse.toString());
        aiChatSession.setThinking(thinkingBuffer.toString());
        context.getDependencies().sessionService().updateSession(aiChatSession);
    }

    @Override
    public PptInstStatus getTargetStatus() {
        return TARGET_STATUS;
    }
}
