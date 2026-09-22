package com.genchat.foundation;

import com.agent.foundation.tool.DefaultToolExecutor;
import com.agent.foundation.tool.ToolDefinition;
import com.agent.foundation.tool.ToolExecutor;
import com.genchat.common.utils.JacksonJson;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.ToolCallback;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Bridges Spring AI ToolCallback into foundation ToolExecutor.
 */
@Slf4j
public final class FoundationToolExecutorFactory {

    private FoundationToolExecutorFactory() {
    }

    public static ToolExecutor fromToolCallbacks(List<ToolCallback> toolCallbacks) {
        DefaultToolExecutor executor = new DefaultToolExecutor();
        if (toolCallbacks == null || toolCallbacks.isEmpty()) {
            return executor;
        }

        for (ToolCallback callback : toolCallbacks) {
            if (callback == null || callback.getToolDefinition() == null) {
                continue;
            }
            String name = callback.getToolDefinition().name();
            if (name == null || name.isBlank()) {
                continue;
            }
            String description = Objects.toString(callback.getToolDefinition().description(), "");
            executor.register(new ToolDefinition(name, description, false, callback.getToolDefinition().inputSchema()),
                    args -> invokeCallback(name, callback, args));
        }
        return executor;
    }

    private static String invokeCallback(String toolName, ToolCallback callback, Map<String, Object> args) {
        try {
            String request = JacksonJson.toJson(args == null ? Map.of() : args);
            log.info("tool.callback.invoke toolName={} args={}", toolName, request);
            Object result = callback.call(request);
            String normalized = normalizeResult(result);
            log.info("tool.callback.success toolName={} resultLength={}", toolName, normalized == null ? 0 : normalized.length());
            return FoundationToolPayloads.encode(toolName, args, null, normalized);
        } catch (Exception ex) {
            log.warn("tool.callback.failed toolName={} error={}", toolName, ex.getMessage());
            return FoundationToolPayloads.encode(
                    toolName,
                    args,
                    null,
                    JacksonJson.toJson(Map.of("error", Objects.toString(ex.getMessage(), "tool execution failed"))));
        }
    }

    private static String normalizeResult(Object result) {
        if (result == null) {
            return "";
        }
        if (result instanceof String text) {
            return text;
        }
        if (result instanceof Map<?, ?> || result instanceof Collection<?>) {
            return JacksonJson.toJson(result);
        }
        return Objects.toString(result, "");
    }

}

