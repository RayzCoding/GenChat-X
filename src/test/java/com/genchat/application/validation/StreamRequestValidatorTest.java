package com.genchat.application.validation;

import com.genchat.common.error.ApiException;
import com.genchat.common.error.StreamErrorCode;
import com.genchat.dto.UnifiedAgentStreamRequest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
    void requireQuestionFluxReturnsCodedApiException() {
        var error = assertThrows(ApiException.class,
                () -> StreamRequestValidator.requireQuestionFlux("").blockFirst());
        assertEquals(StreamErrorCode.QUESTION_EMPTY.code(), error.getCode());
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
    void unifiedStreamValidationReturnsModeCodeForUnsupportedMode() {
        var request = new UnifiedAgentStreamRequest("invalid_mode", "hello", null, "conv-1", null, null);
        var error = assertThrows(ApiException.class,
                () -> StreamRequestValidator.validateUnifiedStreamFlux(request).blockFirst());
        assertEquals(StreamErrorCode.MODE_UNSUPPORTED.code(), error.getCode());
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
