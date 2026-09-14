package com.genchat.agent;

import com.genchat.agent.core.ReactStreamRequest;
import com.genchat.dto.AiChatSession;
import com.genchat.service.AgentTaskService;
import com.genchat.service.AiChatSessionService;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ComposableReactAgentTest {

    private static ComposableReactAgent newAgent(ComposableReactAgent.Mode mode,
                                                 AiChatSessionService sessionService) {
        return new ComposableReactAgent(
                mode,
                mock(ChatModel.class),
                sessionService,
                mock(AgentTaskService.class),
                new ToolCallback[0],
                5);
    }

    @Test
    void chatModeUsesWebSearchAgentType() {
        var agent = newAgent(ComposableReactAgent.Mode.CHAT, mock(AiChatSessionService.class));
        assertEquals("webSearchReactAgent", agent.getAgentType());
        assertNotNull(agent.getPrimarySystemPrompt());
    }

    @Test
    void fileModeUsesAsciiHistoryLabel() {
        var agent = newAgent(ComposableReactAgent.Mode.FILE, mock(AiChatSessionService.class));
        assertEquals("Conversation history:", agent.getHistoryLabel());
    }

    @Test
    void skillsModeDisablesAgentState() {
        var agent = newAgent(ComposableReactAgent.Mode.SKILLS, mock(AiChatSessionService.class));
        assertNull(agent.createAgentState());
        assertEquals("fileReactAgent", newAgent(ComposableReactAgent.Mode.FILE, mock(AiChatSessionService.class)).getAgentType());
    }

    @Test
    void skillsModeResolvesLatestFileIdWhenRequestFileMissing() {
        var sessionService = mock(AiChatSessionService.class);
        when(sessionService.findLatestFileIdBySessionId("conv-1")).thenReturn(Optional.of("file-9"));
        var agent = newAgent(ComposableReactAgent.Mode.SKILLS, sessionService);

        AiChatSession session = agent.buildSessionForSave(ReactStreamRequest.of("conv-1", "q"));
        assertEquals("file-9", session.getFileid());
    }
}
