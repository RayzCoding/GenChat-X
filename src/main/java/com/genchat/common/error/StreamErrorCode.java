package com.genchat.common.error;

/**
 * Error codes for agent stream request validation and routing.
 */
public enum StreamErrorCode {
    REQUEST_NULL(40001, "request is null"),
    QUESTION_EMPTY(40002, "question is null or empty"),
    CONVERSATION_ID_EMPTY(40003, "conversationId is null or empty"),
    FILE_ID_EMPTY(40004, "fileId is null or empty"),
    MODE_UNSUPPORTED(40005, "unsupported mode");

    private final int code;
    private final String defaultMessage;

    StreamErrorCode(int code, String defaultMessage) {
        this.code = code;
        this.defaultMessage = defaultMessage;
    }

    public int code() {
        return code;
    }

    public String defaultMessage() {
        return defaultMessage;
    }
}
