package com.genchat.application.audit;

import com.genchat.agent.model.AgentState;
import com.genchat.application.stream.AgentStreamLifecycle;
import com.genchat.service.AiChatSessionService;
import org.slf4j.Logger;

/**
 * Shared persistence and completion logging for agent stream executions.
 */
public final class AgentAuditSupport {

    private AgentAuditSupport() {
    }

    public static void persistSession(AiChatSessionService sessionService,
                                      Long sessionId,
                                      AgentStreamLifecycle.StreamBuffers buffers,
                                      AgentState agentState,
                                      long totalResponseTime,
                                      long firstResponseTime,
                                      String usedTools,
                                      String recommendations,
                                      String agentType,
                                      String referencesJson) {
        sessionService.update(
                sessionId,
                buffers.finalAnswer(),
                buffers.thinking(),
                agentState,
                totalResponseTime,
                firstResponseTime,
                usedTools,
                recommendations,
                agentType,
                referencesJson);
    }

    public static void logCompletion(Logger logger,
                                     String agentType,
                                     String conversationId,
                                     long totalResponseTime,
                                     long firstResponseTime) {
        logger.info("agent.stream.completed agentType={} conversationId={} totalMs={} firstMs={}",
                agentType,
                conversationId,
                totalResponseTime,
                firstResponseTime);
    }
}
