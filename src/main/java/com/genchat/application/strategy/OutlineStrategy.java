package com.genchat.application.strategy;

import com.agent.foundation.model.RunnableParams;
import com.genchat.common.AgentStreamEvent;
import com.genchat.common.prompts.PptBuilderPrompts;
import com.genchat.dto.AiPptInst;
import com.genchat.entity.PptInstStatus;
import com.genchat.foundation.FoundationReactAgentFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Sinks;

@Slf4j
@Component
public class OutlineStrategy implements PptStateStrategy {
    private static final PptInstStatus TARGET_STATUS = PptInstStatus.SCHEMA;

    @Override
    public void execute(AiPptInst inst,
                        Sinks.Many<String> sink,
                        String question,
                        StringBuilder thinkingBuffer,
                        PptStateStrategyContext context) {
        // loading template by code
        var templateCode = inst.getTemplateCode();
        var templateOptional = context.getDependencies().pptTemplateService().getByTemplateCode(templateCode);
        if (templateOptional.isEmpty()) {
            log.info("Template not found for templateCode {}", templateCode);
            inst.setErrorMsg("Template not found for templateCode: " + templateCode);
            inst.setStatus(PptInstStatus.TEMPLATE.getCode());
            context.getDependencies().pptInstService().updateInst(inst);
            context.getDependencies().strategyFactory().executeFailedStrategy(inst,
                    sink, question, thinkingBuffer, context);
            return;
        }
        // Generate an outline based on the template's schema and search information
        var pptTemplate = templateOptional.get();
        var outlinePrompt = PptBuilderPrompts.getOutlinePrompt(inst.getRequirement(), pptTemplate.getTemplateSchema(),
                pptTemplate.getTemplateName(), inst.getSearchInfo());
        var outlineContent = new StringBuilder();
        var agent = FoundationReactAgentFactory.create(
                context.getDependencies().chatModel(),
                context.getTools(),
                5,
                0,
                true);
        var disposable = agent.streamForEvents(
                        outlinePrompt,
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
                        outlineContent.append(chunk.content());
                    }
                })
                .doOnComplete(() -> {
                    log.info("Outline generate completed.");
                    inst.setStatus(TARGET_STATUS.getCode());
                    inst.setOutline(outlineContent.toString());
                    context.getDependencies().pptInstService().updateInst(inst);
                    sink.tryEmitNext(new AgentStreamEvent.Thinking("\n ✅ After the outline is generated, start designing the PPT details\n").toJSON());
                    context.continueStateMachine(inst, sink, question, thinkingBuffer);
                })
                .doOnError(err -> {
                    log.info("Outline generate failed.", err);
                    inst.setStatus(PptInstStatus.OUTLINE.getCode());
                    inst.setErrorMsg("Outline generate failed: " + err.getMessage());
                    context.getDependencies().pptInstService().updateInst(inst);
                    context.getDependencies().strategyFactory().executeFailedStrategy(inst, sink, question, thinkingBuffer, context);
                })
                .subscribe();

        context.setDisposable(inst.getConversationId(), disposable);
    }

    @Override
    public PptInstStatus getTargetStatus() {
        return TARGET_STATUS;
    }
}
