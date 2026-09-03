package com.genchat.foundation;

import com.agent.foundation.tool.DefaultToolExecutor;
import com.agent.foundation.tool.ToolDefinition;
import com.agent.foundation.tool.ToolExecutor;
import com.genchat.common.utils.JacksonJson;
import org.springframework.ai.tool.ToolCallback;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Bridges Spring AI ToolCallback into foundation ToolExecutor.
 */
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
            executor.register(new ToolDefinition(name, description, false), args -> invokeCallback(name, callback, args));
        }
        return executor;
    }

    private static String invokeCallback(String toolName, ToolCallback callback, Map<String, Object> args) {
        try {
            String request = JacksonJson.toJson(args == null ? Map.of() : args);
            Object result = callback.call(request);
            return FoundationToolPayloads.encode(toolName, args, null, normalizeResult(result));
        } catch (Exception ex) {
            return FoundationToolPayloads.encode(
                    toolName,
                    args,
                    null,
                    "{\"error\":\"" + sanitize(ex.getMessage()) + "\"}");
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

    private static String sanitize(String value) {
        if (value == null || value.isBlank()) {
            return "tool execution failed";
        }
        return value.replace('"', '\'');
    }
}

