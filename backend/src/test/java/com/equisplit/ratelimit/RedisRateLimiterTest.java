package com.equisplit.ratelimit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RedisRateLimiterTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    private RedisRateLimiter rateLimiter;

    @BeforeEach
    void setUp() {
        rateLimiter = new RedisRateLimiter(redisTemplate);
    }

    @Test
    @DisplayName("Request under limit is allowed (e.g. 1st request)")
    void testUnderLimitAllowed() {
        when(redisTemplate.execute(any(RedisScript.class), eq(List.of("rate_limit:login:127.0.0.1")), eq("60")))
                .thenReturn(List.of(1L, 60L));

        RateLimitResult result = rateLimiter.checkLimit("rate_limit:login", "127.0.0.1", 5, 60);

        assertThat(result.allowed()).isTrue();
        assertThat(result.currentCount()).isEqualTo(1L);
        assertThat(result.retryAfterSeconds()).isEqualTo(0L);
    }

    @Test
    @DisplayName("Request exactly at limit is allowed (e.g. 5th request)")
    void testAtLimitAllowed() {
        when(redisTemplate.execute(any(RedisScript.class), eq(List.of("rate_limit:login:127.0.0.1")), eq("60")))
                .thenReturn(List.of(5L, 45L));

        RateLimitResult result = rateLimiter.checkLimit("rate_limit:login", "127.0.0.1", 5, 60);

        assertThat(result.allowed()).isTrue();
        assertThat(result.currentCount()).isEqualTo(5L);
        assertThat(result.retryAfterSeconds()).isEqualTo(0L);
    }

    @Test
    @DisplayName("Request over limit is rejected with retryAfter matching TTL (e.g. 6th request)")
    void testOverLimitRejected() {
        when(redisTemplate.execute(any(RedisScript.class), eq(List.of("rate_limit:login:127.0.0.1")), eq("60")))
                .thenReturn(List.of(6L, 42L));

        RateLimitResult result = rateLimiter.checkLimit("rate_limit:login", "127.0.0.1", 5, 60);

        assertThat(result.allowed()).isFalse();
        assertThat(result.currentCount()).isEqualTo(6L);
        assertThat(result.retryAfterSeconds()).isEqualTo(42L);
    }

    @Test
    @DisplayName("TTL <= 0 still enforces at least 1 second retryAfter")
    void testMinimumRetryAfter() {
        when(redisTemplate.execute(any(RedisScript.class), eq(List.of("rate_limit:login:127.0.0.1")), eq("60")))
                .thenReturn(List.of(7L, 0L));

        RateLimitResult result = rateLimiter.checkLimit("rate_limit:login", "127.0.0.1", 5, 60);

        assertThat(result.allowed()).isFalse();
        assertThat(result.retryAfterSeconds()).isEqualTo(1L);
    }

    @Test
    @DisplayName("Fail-open: Redis connection failure allows request to proceed")
    void testFailOpenOnRedisError() {
        when(redisTemplate.execute(any(RedisScript.class), any(), any()))
                .thenThrow(new RedisConnectionFailureException("Could not connect to Redis"));

        RateLimitResult result = rateLimiter.checkLimit("rate_limit:login", "127.0.0.1", 5, 60);

        assertThat(result.allowed()).isTrue();
        assertThat(result.retryAfterSeconds()).isEqualTo(0L);
    }
}
