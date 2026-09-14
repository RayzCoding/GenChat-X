package com.genchat.dto;

import com.genchat.application.validation.StreamRequestValidator;
import org.springframework.util.StringUtils;

/**
 * Request body for unified /agent/stream endpoint.
 */
public record UnifiedAgentStreamRequest(
        String mode,
        String question,
        String query,
        String conversationId,
        String conversationsId,
        String fileId
) {

    public UnifiedAgentMode resolvedMode() {
        return UnifiedAgentMode.from(mode);
    }

    public String resolvedQuestion() {
        if (StringUtils.hasLength(question)) {
            return question;
        }
        return query;
    }

    public String resolvedConversationId() {
        return StreamRequestValidator.resolveConversationId(conversationId, conversationsId);
    }
}
