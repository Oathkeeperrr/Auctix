package com.auctix.exception;

import java.util.Map;

/**
 * Business-rule failure carrying a stable {@link ErrorCode}.
 */
public class ApiException extends RuntimeException {

    private final ErrorCode code;
    private final transient Map<String, Object> details;

    public ApiException(ErrorCode code) {
        this(code, code.getDefaultMessage(), Map.of());
    }

    public ApiException(ErrorCode code, String message) {
        this(code, message, Map.of());
    }

    public ApiException(ErrorCode code, String message, Map<String, Object> details) {
        super(message);
        this.code = code;
        this.details = details == null ? Map.of() : details;
    }

    public static ApiException notFound(ErrorCode code, Object id) {
        return new ApiException(code, code.getDefaultMessage(), Map.of("id", String.valueOf(id)));
    }

    public ErrorCode getCode() {
        return code;
    }

    public Map<String, Object> getDetails() {
        return details;
    }
}
