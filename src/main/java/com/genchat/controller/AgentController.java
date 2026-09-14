package com.genchat.controller;

import com.genchat.application.agent.AgentFacade;
import com.genchat.application.validation.StreamRequestValidator;
import com.genchat.dto.StopAgentResponse;
import com.genchat.dto.UnifiedAgentStreamRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/agent")
@RequiredArgsConstructor
@Slf4j
public class AgentController {

    private final AgentFacade agentFacade;

    @PostMapping(value = "/stream", produces = "text/event-stream;charset=UTF-8")
    public Flux<String> unifiedStream(@RequestBody UnifiedAgentStreamRequest request) {
        log.info("received request to unified stream, mode: {}, conversationId: {}",
                request != null ? request.mode() : null,
                request != null ? StreamRequestValidator.resolveConversationId(request.conversationId(), request.conversationsId()) : null);
        var error = StreamRequestValidator.validateUnifiedStreamFlux(request);
        if (error != null) {
            return error;
        }
        return agentFacade.unifiedStream(request);
    }

    @GetMapping(value = "/chat/stream", produces = "text/event-stream;charset=UTF-8")
    public Flux<String> chat(@RequestParam String conversationId,
                             @RequestParam String question) {
        log.info("received request to chat stream, conversationId: {}, question: {}",
                conversationId, question);
        var error = StreamRequestValidator.requireConversationAndQuestionFlux(conversationId, question);
        if (error != null) {
            return error;
        }
        return agentFacade.chatStream(conversationId, question);
    }

    @GetMapping(value = "/deep/stream", produces = "text/event-stream;charset=UTF-8")
    public Flux<String> deepStream(@RequestParam String question,
                                   @RequestParam(required = false) String conversationId,
                                   @RequestParam(required = false) String conversationsId) {
        var resolvedConversationId = StreamRequestValidator.resolveConversationId(conversationId, conversationsId);
        log.info("Receive question, question: {}, conversationId: {}", question, resolvedConversationId);
        var questionError = StreamRequestValidator.requireQuestionFlux(question);
        if (questionError != null) {
            return questionError;
        }
        var conversationError = StreamRequestValidator.requireConversationIdFlux(resolvedConversationId);
        if (conversationError != null) {
            return conversationError;
        }
        return agentFacade.deepStream(question, resolvedConversationId);
    }

    @GetMapping(value = "/simple/stream", produces = "text/event-stream;charset=UTF-8")
    public Flux<String> simpleChat(@RequestParam String question) {
        log.info("received request to simple chat stream, question: {}", question);
        var error = StreamRequestValidator.requireQuestionFlux(question);
        if (error != null) {
            return error;
        }
        return agentFacade.simpleStream(question);
    }

    @GetMapping(value = "/file/stream", produces = "text/event-stream;charset=UTF-8")
    public Flux<String> fileStream(@RequestParam String question,
                                   @RequestParam String conversationId,
                                   @RequestParam String fileId) {
        log.info("Receive file question, question: {}, conversationId: {}, fileId: {}",
                question, conversationId, fileId);
        var error = StreamRequestValidator.requireFileStreamParamsFlux(conversationId, question, fileId);
        if (error != null) {
            return error;
        }
        return agentFacade.fileStream(conversationId, question, fileId);
    }

    @GetMapping(value = "/ppt/stream", produces = "text/event-stream;charset=UTF-8")
    public Flux<String> pptStream(@RequestParam String question,
                                  @RequestParam(required = false) String conversationId,
                                  @RequestParam(required = false) String conversationsId) {
        var resolvedConversationId = StreamRequestValidator.resolveConversationId(conversationId, conversationsId);
        log.info("Received ppt question, question: {}, conversationId: {}", question, resolvedConversationId);
        var error = StreamRequestValidator.requireQuestionFlux(question);
        if (error != null) {
            return error;
        }
        return agentFacade.pptStream(resolvedConversationId, question);
    }

    @GetMapping(value = "/skills/stream", produces = "text/event-stream;charset=UTF-8")
    public Flux<String> skillsStream(@RequestParam String question,
                                     @RequestParam(required = false) String conversationId,
                                     @RequestParam(required = false) String conversationsId,
                                     @RequestParam(required = false) String fileId) {
        var resolvedConversationId = StreamRequestValidator.resolveConversationId(conversationId, conversationsId);
        log.info("Received skills question, question: {}, conversationId: {}, fileId: {}",
                question, resolvedConversationId, fileId);
        var error = StreamRequestValidator.requireQuestionFlux(question);
        if (error != null) {
            return error;
        }
        return agentFacade.skillsStream(resolvedConversationId, question, fileId);
    }

    @GetMapping("/stop")
    public StopAgentResponse stopAgent(@RequestParam String conversationId) {
        return agentFacade.stopAgent(conversationId);
    }
}
