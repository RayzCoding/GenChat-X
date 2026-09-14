package com.genchat.common.error;

/**
 * Runtime exception with a stable numeric error code.
 */
public class ApiException extends RuntimeException {

    private final int code;

    public ApiException(StreamErrorCode errorCode) {
        super(errorCode.defaultMessage());
        this.code = errorCode.code();
    }

    public ApiException(StreamErrorCode errorCode, String message) {
        super(message);
        this.code = errorCode.code();
    }

    public int getCode() {
        return code;
    }
}
