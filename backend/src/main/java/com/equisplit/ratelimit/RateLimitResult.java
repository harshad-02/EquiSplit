package com.equisplit.ratelimit;

public record RateLimitResult(
        boolean allowed,
        long currentCount,
        long retryAfterSeconds
) {
    public static RateLimitResult allowed(long count) {
        return new RateLimitResult(true, count, 0L);
    }

    public static RateLimitResult failOpen() {
        return new RateLimitResult(true, 0L, 0L);
    }

    public static RateLimitResult rejected(long count, long retryAfterSeconds) {
        return new RateLimitResult(false, count, Math.max(1L, retryAfterSeconds));
    }
}
