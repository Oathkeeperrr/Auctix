package com.auctix.exception;

import java.time.Instant;
import java.util.Map;

/**
 * Uniform error body for every failed request.
 */
public record ApiError(
        String code,
        String message,
        int status,
        String path,
        Instant timestamp,
        Map<String, Object> details
) {
    public static ApiError of(ErrorCode code, String message, String path, Map<String, Object> details) {
        return new ApiError(code.name(), message, code.getStatus().value(), path, Instant.now(),
                details == null ? Map.of() : details);
    }
}
