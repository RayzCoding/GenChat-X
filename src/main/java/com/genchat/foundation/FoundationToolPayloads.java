package com.genchat.foundation;

import com.genchat.common.utils.JacksonJson;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Encodes and decodes tool execution payloads transferred through foundation stream events.
 */
public final class FoundationToolPayloads {

    private static final String MARKER = "_genchatFoundationTool";

    private FoundationToolPayloads() {
    }

    public record ToolPayload(String toolName, String toolCallId, String argumentsJson, String result) {
    }

    public static String encode(String toolName, Map<String, Object> arguments, String toolCallId, String result) {
        var payload = new LinkedHashMap<String, Object>();
        payload.put(MARKER, true);
        payload.put("toolName", Objects.toString(toolName, "unknown_tool"));
        payload.put("toolCallId", toolCallId);
        payload.put("arguments", arguments == null ? Map.of() : arguments);
        payload.put("result", Objects.toString(result, ""));
        return JacksonJson.toJson(payload);
    }

    @SuppressWarnings("unchecked")
    public static ToolPayload decode(String payload) {
        var node = JacksonJson.parseTreeLenient(payload);
        if (node == null || !node.path(MARKER).asBoolean(false)) {
            return new ToolPayload("unknown_tool", null, null, Objects.toString(payload, ""));
        }
        String toolName = node.path("toolName").asText("unknown_tool");
        String toolCallId = node.path("toolCallId").isNull() ? null : node.path("toolCallId").asText();
        String argumentsJson = null;
        var argsNode = node.get("arguments");
        if (argsNode != null && !argsNode.isNull()) {
            argumentsJson = JacksonJson.toJson(argsNode);
        }
        String result = node.path("result").isMissingNode() || node.path("result").isNull()
                ? ""
                : node.path("result").asText("");
        return new ToolPayload(toolName, toolCallId, argumentsJson, result);
    }
}

