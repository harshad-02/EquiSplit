package com.equisplit.ratelimit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

@Component
public class RedisRateLimiter {

    private static final Logger log = LoggerFactory.getLogger(RedisRateLimiter.class);

    private final StringRedisTemplate redisTemplate;

    // Atomic fixed-window rate limiter Lua script:
    // 1. INCR counter
    // 2. If first request (current == 1), set expiration (windowSeconds)
    // 3. Return {current, TTL}
    private static final String RATE_LIMIT_LUA = """
            local current = redis.call('INCR', KEYS[1])
            if current == 1 then
                redis.call('EXPIRE', KEYS[1], ARGV[1])
            end
            local ttl = redis.call('TTL', KEYS[1])
            return {current, ttl}
            """;

    @SuppressWarnings("rawtypes")
    private final RedisScript<List> rateLimitScript;

    public RedisRateLimiter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        DefaultRedisScript<List> script = new DefaultRedisScript<>();
        script.setScriptText(RATE_LIMIT_LUA);
        script.setResultType(List.class);
        this.rateLimitScript = script;
    }

    /**
     * Checks and increments rate limit counter for the given prefix, IP, limit and window.
     * Guaranteed fail-open: if Redis is unavailable or times out, logs error and allows the request.
     */
    public RateLimitResult checkLimit(String keyPrefix, String ip, int limit, int windowSeconds) {
        String key = keyPrefix + ":" + ip;

        try {
            List<?> result = redisTemplate.execute(
                    rateLimitScript,
                    Collections.singletonList(key),
                    String.valueOf(windowSeconds)
            );

            if (result != null && result.size() >= 2) {
                long currentCount = ((Number) result.get(0)).longValue();
                long ttl = ((Number) result.get(1)).longValue();

                if (currentCount > limit) {
                    return RateLimitResult.rejected(currentCount, ttl);
                }
                return RateLimitResult.allowed(currentCount);
            }

            return RateLimitResult.allowed(1);
        } catch (Exception e) {
            log.error("Rate limiter Redis infrastructure error for key {}: {}. Failing open.", key, e.getMessage());
            // Fail open: if Redis is unavailable, don't make the entire API unavailable.
            return RateLimitResult.failOpen();
        }
    }
}
