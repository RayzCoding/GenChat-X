package com.genchat.dto;

import java.util.Locale;

/**
 * Unified stream mode values for the single /agent/stream endpoint.
 */
public enum UnifiedAgentMode {
    CHAT,
    SIMPLE,
    FILE,
    DEEP,
    PPT,
    SKILLS;

    public static UnifiedAgentMode from(String raw) {
        if (raw == null || raw.isBlank()) {
            return CHAT;
        }
        String normalized = raw.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "CHAT", "WEB", "WEB_SEARCH" -> CHAT;
            case "SIMPLE" -> SIMPLE;
            case "FILE", "RAG" -> FILE;
            case "DEEP", "DEEP_RESEARCH" -> DEEP;
            case "PPT" -> PPT;
            case "SKILLS", "SKILL" -> SKILLS;
            default -> throw new IllegalArgumentException("Unsupported mode: " + raw);
        };
    }
}
