package com.genchat.application.strategy;

import com.agent.foundation.output.StructuredOutputException;
import com.genchat.agent.PptIntentRecognizer;
import com.genchat.dto.AiPptInst;
import com.genchat.dto.AiPptTemplate;
import com.genchat.entity.PptInstStatus;
import com.genchat.service.AiPptInstService;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Sinks;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PptStructuredFailureTest {
    private static final String SCHEMA = """
            {"slides":[{"pageType":"COVER","templatePageIndex":1,"data":{
              "title":{"type":"text","content":"Original","fontLimit":20}}}]}
            """;

    @Test
    void failedIntentRecognitionNeverDefaultsToCreate() {
        var model = invalidModel();
        var instances = mock(AiPptInstService.class);
        when(instances.getLatestInst("c")).thenReturn(instance());
        var recognizer = new PptIntentRecognizer(model, instances);
        assertThrows(StructuredOutputException.class, () -> recognizer.recognize("c", "modify the title"));
        verify(model, times(3)).call(any(Prompt.class));
    }

    @Test
    void failedModificationKeepsOriginalSuccessfulInstanceAndDoesNotRunToolsOrImages() {
        var context = context(invalidModel(), SCHEMA);
        var inst = instance();
        Sinks.Many<String> sink = Sinks.many().unicast().onBackpressureBuffer();
        new SchemaStrategy().executeWithModifyPrompt(inst, sink, "modify title", new StringBuilder(), context, "modify title");
        assertThrows(StructuredOutputException.class, () -> sink.asFlux().blockLast(Duration.ofSeconds(10)));
        assertEquals(PptInstStatus.SUCCESS.getCode(), inst.getStatus());
        assertEquals(SCHEMA, inst.getPptSchema());
        assertNull(inst.getErrorMsg());
        verify(context.getDependencies().pptInstService(), never()).updateInst(any());
        verify(context, never()).getTools();
        verify(context, never()).continueStateMachine(any(), any(), any(), any());
        verifyNoInteractions(context.getDependencies().imageGenerationService());
        verifyNoInteractions(context.getDependencies().strategyFactory());
    }

    @Test
    void invalidTemplateIsRejectedBeforeAnyModelOrImageRequest() {
        var model = invalidModel();
        var context = context(model, "{}");
        Sinks.Many<String> sink = Sinks.many().unicast().onBackpressureBuffer();
        new SchemaStrategy().execute(instance(), sink, "generate", new StringBuilder(), context);
        assertThrows(IllegalArgumentException.class, () -> sink.asFlux().blockLast(Duration.ofSeconds(10)));
        verifyNoInteractions(model);
        verifyNoInteractions(context.getDependencies().imageGenerationService());
        verify(context, never()).continueStateMachine(any(), any(), any(), any());
    }

    @Test
    void imageTasksAreExecutedOnlyOnceAcrossMultipleSlides() {
        String page = "{\"pageType\":\"CONTENT\",\"templatePageIndex\":1,\"data\":{\"photo\":{\"type\":\"image\",\"content\":\"landscape\"}}}";
        var model = mock(ChatModel.class);
        when(model.call(any(Prompt.class))).thenReturn(response("{\"slides\":[" + page + "," + page + "]}"));
        var context = context(model, "{\"slides\":[" + page + "]}");
        when(context.getDependencies().imageGenerationService().generateImage("landscape"))
                .thenThrow(new IllegalStateException("image service unavailable"));
        Sinks.Many<String> sink = Sinks.many().unicast().onBackpressureBuffer();
        doAnswer(invocation -> { sink.tryEmitComplete(); return null; })
                .when(context).continueStateMachine(any(), any(), any(), any());
        new SchemaStrategy().execute(instance(), sink, "generate", new StringBuilder(), context);
        sink.asFlux().blockLast(Duration.ofSeconds(10));
        verify(context.getDependencies().imageGenerationService(), times(2)).generateImage("landscape");
        verify(model).call(any(Prompt.class));
    }

    private static PptStateStrategyContext context(ChatModel model, String templateSchema) {
        var context = mock(PptStateStrategyContext.class, RETURNS_DEEP_STUBS);
        when(context.getDependencies().chatModel()).thenReturn(model);
        when(context.getDependencies().pptTemplateService().getByTemplateCode("known"))
                .thenReturn(Optional.of(AiPptTemplate.builder().templateCode("known").slideCount(1)
                        .templateSchema(templateSchema).build()));
        return context;
    }

    private static AiPptInst instance() {
        return AiPptInst.builder().conversationId("c").templateCode("known").outline("outline")
                .pptSchema(SCHEMA).status(PptInstStatus.SUCCESS.getCode()).build();
    }

    private static ChatModel invalidModel() {
        var model = mock(ChatModel.class);
        when(model.call(any(Prompt.class))).thenReturn(response("not JSON"));
        return model;
    }

    private static ChatResponse response(String text) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
    }
}

