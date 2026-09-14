package com.genchat.application.validation;

import com.genchat.dto.UnifiedAgentMode;
import com.genchat.dto.UnifiedAgentStreamRequest;
import com.genchat.common.error.ApiException;
import com.genchat.common.error.StreamErrorCode;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;

/**
 * Validates agent stream HTTP request parameters.
 */
public final class StreamRequestValidator {

    private StreamRequestValidator() {
    }

    /**
     * Returns a failed flux when validation fails; {@code null} means the request is valid.
     */
    public static Flux<String> requireQuestionFlux(String question) {
        if (!StringUtils.hasLength(question)) {
            return Flux.error(new ApiException(StreamErrorCode.QUESTION_EMPTY));
        }
        return null;
    }

    /**
     * Returns a failed flux when validation fails; {@code null} means the request is valid.
     */
    public static Flux<String> requireConversationAndQuestionFlux(String conversationId, String question) {
        var questionError = requireQuestionFlux(question);
        if (questionError != null) {
            return questionError;
        }
        if (ObjectUtils.isEmpty(conversationId)) {
            return Flux.error(new ApiException(StreamErrorCode.CONVERSATION_ID_EMPTY));
        }
        return null;
    }

    /**
     * Prefer `conversationId` and fallback to legacy `conversationsId`.
     */
    public static String resolveConversationId(String conversationId, String conversationsId) {
        if (StringUtils.hasLength(conversationId)) {
            return conversationId;
        }
        return conversationsId;
    }

    /**
     * Returns a failed flux when validation fails; {@code null} means the request is valid.
     */
    public static Flux<String> requireConversationIdFlux(String conversationId) {
        if (ObjectUtils.isEmpty(conversationId)) {
            return Flux.error(new ApiException(StreamErrorCode.CONVERSATION_ID_EMPTY));
        }
        return null;
    }

    /**
     * Returns a failed flux when validation fails; {@code null} means the request is valid.
     */
    public static Flux<String> requireFileStreamParamsFlux(String conversationId, String question, String fileId) {
        var questionError = requireQuestionFlux(question);
        if (questionError != null) {
            return questionError;
        }
        if (ObjectUtils.isEmpty(conversationId) || ObjectUtils.isEmpty(fileId)) {
            if (ObjectUtils.isEmpty(conversationId)) {
                return Flux.error(new ApiException(StreamErrorCode.CONVERSATION_ID_EMPTY));
            }
            return Flux.error(new ApiException(StreamErrorCode.FILE_ID_EMPTY));
        }
        return null;
    }

    /**
     * Returns a failed flux when unified stream validation fails; {@code null} means valid.
     */
    public static Flux<String> validateUnifiedStreamFlux(UnifiedAgentStreamRequest request) {
        if (request == null) {
            return Flux.error(new ApiException(StreamErrorCode.REQUEST_NULL));
        }

        String question = request.resolvedQuestion();
        var questionError = requireQuestionFlux(question);
        if (questionError != null) {
            return questionError;
        }

        UnifiedAgentMode mode;
        try {
            mode = request.resolvedMode();
        } catch (IllegalArgumentException ex) {
            return Flux.error(new ApiException(StreamErrorCode.MODE_UNSUPPORTED, ex.getMessage()));
        }

        String conversationId = request.resolvedConversationId();
        return switch (mode) {
            case CHAT, DEEP -> requireConversationIdFlux(conversationId);
            case FILE -> {
                if (!StringUtils.hasLength(request.fileId())) {
                    yield Flux.error(new ApiException(StreamErrorCode.FILE_ID_EMPTY));
                }
                yield requireConversationIdFlux(conversationId);
            }
            case SIMPLE, PPT, SKILLS -> null;
        };
    }
}
