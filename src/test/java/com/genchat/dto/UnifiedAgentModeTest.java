package com.genchat.dto;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UnifiedAgentModeTest {

    @Test
    void fromReturnsChatWhenModeMissing() {
        assertEquals(UnifiedAgentMode.CHAT, UnifiedAgentMode.from(null));
    }

    @Test
    void fromSupportsAliases() {
        assertEquals(UnifiedAgentMode.CHAT, UnifiedAgentMode.from("web_search"));
        assertEquals(UnifiedAgentMode.FILE, UnifiedAgentMode.from("rag"));
        assertEquals(UnifiedAgentMode.DEEP, UnifiedAgentMode.from("deep_research"));
    }

    @Test
    void fromRejectsUnknownMode() {
        assertThrows(IllegalArgumentException.class, () -> UnifiedAgentMode.from("unknown_mode"));
    }
}
