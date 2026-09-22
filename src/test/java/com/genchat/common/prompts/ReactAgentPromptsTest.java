package com.genchat.common.prompts;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReactAgentPromptsTest {

    @Test
    void filePromptUsesActualToolName() {
        String prompt = ReactAgentPrompts.getFilePrompt();
        assertTrue(prompt.contains("loadFileContents"), "file prompt should reference the real tool name");
    }

    @Test
    void reactPromptRequiresSequentialCallsWithoutJavaFragments() {
        String prompt = ReactAgentPrompts.getReactAgentSystemPrompt();
        assertTrue(prompt.contains("at most one tool call per response"));
        assertFalse(prompt.contains("public static"));
        assertFalse(prompt.contains("\"\"\""));
        assertFalse(prompt.contains("%s"));
    }
}

