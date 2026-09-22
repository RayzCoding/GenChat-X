package com.genchat.application.strategy;

import com.agent.foundation.core.SpringAiChatModelAdapter;
import com.agent.foundation.output.StructuredOutputSupport;
import com.genchat.common.AgentStreamEvent;
import com.genchat.common.validation.StructuredResults;
import com.genchat.common.prompts.PptBuilderPrompts;
import com.genchat.dto.AiPptInst;
import com.genchat.entity.PptInstStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.ai.chat.messages.UserMessage;
import reactor.core.publisher.Sinks;

@Slf4j
@Component
public class TemplateStrategy implements PptStateStrategy {
    private static final PptInstStatus TARGET_STATUS = PptInstStatus.OUTLINE;

    @Override
    public void execute(AiPptInst inst,
                        Sinks.Many<String> sink,
                        String question,
                        StringBuilder thinkingBuffer,
                        PptStateStrategyContext context) {
        sink.tryEmitNext(new AgentStreamEvent.Thinking("Template styling is being designed...\n").toJSON());

        var requirement = inst.getRequirement();
        var pptTemplates = context.getDependencies().pptTemplateService().listAll();
        var templatesInfo = new StringBuilder();
        pptTemplates.forEach(template -> {
            templatesInfo.append(String.format("""
                            --------------------------------
                            template_code: %s
                            template name: %s
                            Applicable styles: %s
                            Number of template pages: %d
                            Template description: %s
                            """,
                    template.getTemplateCode(),
                    template.getTemplateName(),
                    template.getStyleTags(),
                    template.getSlideCount(),
                    template.getTemplateDesc()
            ));
        });
        var templateSelectionPrompt = PptBuilderPrompts.getTemplateSelectionPrompt(requirement, templatesInfo.toString());
        try {
            var outputSpec = StructuredResults.template(pptTemplates.stream()
                    .map(com.genchat.dto.AiPptTemplate::getTemplateCode).collect(java.util.stream.Collectors.toSet()));
            var result = StructuredOutputSupport.generate(new SpringAiChatModelAdapter(context.getDependencies().chatModel()),
                    java.util.List.of(new UserMessage(templateSelectionPrompt)), outputSpec);
            log.info("Template selection result: {}", result);
            inst.setTemplateCode(result.getTemplateCode());
            inst.setStatus(TARGET_STATUS.getCode());
            context.getDependencies().pptInstService().updateInst(inst);
            sink.tryEmitNext(new AgentStreamEvent.Thinking("✅ Once the template is designed, start generating the outline...\n").toJSON());
            context.continueStateMachine(inst, sink, question, thinkingBuffer);
        }catch (Exception e) {
            log.error("Template selection prompt failed", e);
            inst.setStatus(PptInstStatus.TEMPLATE.getCode());
            inst.setErrorMsg(e.getMessage());
            context.getDependencies().pptInstService().updateInst(inst);

            sink.tryEmitError(e);
        }
    }

    @Override
    public PptInstStatus getTargetStatus() {
        return TARGET_STATUS;
    }
}
