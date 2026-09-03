package com.genchat.foundation;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class FoundationToolPayloadsTest {

    @Test
    void decodeReturnsMetadataForEncodedPayload() {
        String encoded = FoundationToolPayloads.encode(
                "tavily_search",
                Map.of("query", "spring ai"),
                "call-1",
                "[{\"text\":\"ok\"}]");

        var decoded = FoundationToolPayloads.decode(encoded);
        assertEquals("tavily_search", decoded.toolName());
        assertEquals("call-1", decoded.toolCallId());
        assertEquals("[{\"text\":\"ok\"}]", decoded.result());
    }

    @Test
    void decodeFallsBackForLegacyRawPayload() {
        var decoded = FoundationToolPayloads.decode("raw-output");
        assertEquals("unknown_tool", decoded.toolName());
        assertNull(decoded.toolCallId());
        assertNull(decoded.argumentsJson());
        assertEquals("raw-output", decoded.result());
    }
}

