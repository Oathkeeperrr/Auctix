package com.auctix.dto.common;

import java.time.Instant;

/**
 * Authoritative clock. The frontend computes {@code offset = serverEpochMillis - clientEpochMillis}
 * and renders every countdown against the corrected time, so browser clock drift cannot
 * change what a user sees.
 */
public record ServerTimeDto(Instant serverTime, long epochMillis) {
    public static ServerTimeDto now() {
        Instant now = Instant.now();
        return new ServerTimeDto(now, now.toEpochMilli());
    }
}
