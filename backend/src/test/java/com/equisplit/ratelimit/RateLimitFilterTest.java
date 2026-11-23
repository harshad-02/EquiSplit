package com.equisplit.ratelimit;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RateLimitFilterTest {

    @Mock
    private RedisRateLimiter rateLimiter;

    @Mock
    private ClientIpResolver ipResolver;

    @Mock
    private FilterChain filterChain;

    private RateLimitFilter filter;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        filter = new RateLimitFilter(
                rateLimiter,
                ipResolver,
                objectMapper
        );
        ReflectionTestUtils.setField(filter, "loginLimit", 5);
        ReflectionTestUtils.setField(filter, "loginWindowSeconds", 60);
        ReflectionTestUtils.setField(filter, "registerLimit", 5);
        ReflectionTestUtils.setField(filter, "registerWindowSeconds", 60);
        ReflectionTestUtils.setField(filter, "expenseLimit", 30);
        ReflectionTestUtils.setField(filter, "expenseWindowSeconds", 60);
        ReflectionTestUtils.setField(filter, "settlementLimit", 10);
        ReflectionTestUtils.setField(filter, "settlementWindowSeconds", 60);
    }

    @Test
    @DisplayName("GET requests bypass rate limiting")
    void testGetRequestsBypass() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/health");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verifyNoInteractions(rateLimiter);
    }

    @Test
    @DisplayName("POST /api/auth/login under limit proceeds to filterChain")
    void testLoginUnderLimitProceeds() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(ipResolver.resolveClientIp(request)).thenReturn("192.168.1.1");
        when(rateLimiter.checkLimit("rate_limit:login", "192.168.1.1", 5, 60))
                .thenReturn(RateLimitResult.allowed(1));

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("POST /api/auth/login exceeding limit returns 429 Too Many Requests with retryAfter")
    void testLoginOverLimitReturns429() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(ipResolver.resolveClientIp(request)).thenReturn("192.168.1.1");
        when(rateLimiter.checkLimit("rate_limit:login", "192.168.1.1", 5, 60))
                .thenReturn(RateLimitResult.rejected(6, 45));

        filter.doFilterInternal(request, response, filterChain);

        verifyNoInteractions(filterChain);
        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(response.getHeader("Retry-After")).isEqualTo("45");

        Map<?, ?> responseBody = objectMapper.readValue(response.getContentAsString(), Map.class);
        assertThat(responseBody.get("message")).isEqualTo("Too many requests. Please try again later.");
        assertThat(responseBody.get("retryAfter")).isEqualTo(45);
    }

    @Test
    @DisplayName("POST /api/auth/register over limit returns 429")
    void testRegisterOverLimitReturns429() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/register");
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(ipResolver.resolveClientIp(request)).thenReturn("10.0.0.2");
        when(rateLimiter.checkLimit("rate_limit:register", "10.0.0.2", 5, 60))
                .thenReturn(RateLimitResult.rejected(6, 30));

        filter.doFilterInternal(request, response, filterChain);

        verifyNoInteractions(filterChain);
        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(response.getHeader("Retry-After")).isEqualTo("30");
    }

    @Test
    @DisplayName("POST /api/groups/{id}/expenses regex matches and enforces expense limit")
    void testExpenseRouteRateLimit() throws ServletException, IOException {
        UUID groupId = UUID.randomUUID();
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/groups/" + groupId + "/expenses");
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(ipResolver.resolveClientIp(request)).thenReturn("10.0.0.3");
        when(rateLimiter.checkLimit("rate_limit:expense", "10.0.0.3", 30, 60))
                .thenReturn(RateLimitResult.rejected(31, 55));

        filter.doFilterInternal(request, response, filterChain);

        verifyNoInteractions(filterChain);
        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(response.getHeader("Retry-After")).isEqualTo("55");
    }

    @Test
    @DisplayName("POST /api/groups/{id}/settlements regex matches and enforces settlement limit")
    void testSettlementRouteRateLimit() throws ServletException, IOException {
        UUID groupId = UUID.randomUUID();
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/groups/" + groupId + "/settlements");
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(ipResolver.resolveClientIp(request)).thenReturn("10.0.0.4");
        when(rateLimiter.checkLimit("rate_limit:settlement", "10.0.0.4", 10, 60))
                .thenReturn(RateLimitResult.rejected(11, 20));

        filter.doFilterInternal(request, response, filterChain);

        verifyNoInteractions(filterChain);
        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(response.getHeader("Retry-After")).isEqualTo("20");
    }
}
