package com.genchat.application.validation;

import com.genchat.dto.UnifiedAgentStreamRequest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class StreamRequestValidatorTest {

    @Test
    void resolveConversationIdPrefersConversationIdParam() {
        assertEquals("primary",
                StreamRequestValidator.resolveConversationId("primary", "legacy"));
    }

    @Test
    void resolveConversationIdFallsBackToConversationsId() {
        assertEquals("legacy",
                StreamRequestValidator.resolveConversationId(null, "legacy"));
    }

    @Test
    void requireQuestionFluxReturnsErrorWhenEmpty() {
        assertNotNull(StreamRequestValidator.requireQuestionFlux(""));
    }

    @Test
    void requireQuestionFluxReturnsNullWhenValid() {
        assertNull(StreamRequestValidator.requireQuestionFlux("hello"));
    }

    @Test
    void unifiedStreamValidationDefaultsToChatAndRequiresConversationId() {
        var request = new UnifiedAgentStreamRequest(null, "hello", null, null, null, null);
        assertNotNull(StreamRequestValidator.validateUnifiedStreamFlux(request));
    }

    @Test
    void unifiedStreamValidationAllowsSimpleWithoutConversationId() {
        var request = new UnifiedAgentStreamRequest("simple", "hello", null, null, null, null);
        assertNull(StreamRequestValidator.validateUnifiedStreamFlux(request));
    }

    @Test
    void unifiedStreamValidationRequiresFileIdForFileMode() {
        var request = new UnifiedAgentStreamRequest("file", "hello", null, "conv-1", null, null);
        assertNotNull(StreamRequestValidator.validateUnifiedStreamFlux(request));
    }

    @Test
    void unifiedStreamValidationAllowsFileModeWhenConversationAndFilePresent() {
        var request = new UnifiedAgentStreamRequest("file", "hello", null, "conv-1", null, "file-1");
        assertNull(StreamRequestValidator.validateUnifiedStreamFlux(request));
    }

    @Test
    void unifiedStreamValidationAcceptsQueryAlias() {
        var request = new UnifiedAgentStreamRequest("simple", null, "hello", null, null, null);
        assertNull(StreamRequestValidator.validateUnifiedStreamFlux(request));
    }
}
