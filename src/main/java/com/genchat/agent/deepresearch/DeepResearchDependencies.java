package com.genchat.agent.deepresearch;

import com.genchat.service.AgentTaskService;
import com.genchat.service.AiChatSessionService;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;

import java.util.List;
import java.util.concurrent.Semaphore;

public record DeepResearchDependencies(
        AiChatSessionService sessionService,
        ChatModel chatModel,
        List<ToolCallback> tools,
        AgentTaskService agentTaskService,
        int maxRounds,
        Semaphore toolSemaphore,
        String agentType
) {
    public DeepResearchDependencies(AiChatSessionService sessionService,
                                    ChatModel chatModel,
                                    List<ToolCallback> tools,
                                    AgentTaskService agentTaskService,
                                    int maxRounds,
                                    int toolSemaphorePermits) {
        this(
                sessionService,
                chatModel,
                tools,
                agentTaskService,
                maxRounds,
                new Semaphore(toolSemaphorePermits),
                "DeepResearchAgent"
        );
    }
}
