package com.equisplit.ratelimit;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private final RedisRateLimiter rateLimiter;
    private final ClientIpResolver ipResolver;
    private final ObjectMapper objectMapper;

    // Pattern for expense creation: POST /api/groups/{groupId}/expenses
    private static final Pattern EXPENSE_PATTERN =
            Pattern.compile("^/api/groups/[a-fA-F0-9\\-]+/expenses/?$");

    // Pattern for settlement recording: POST /api/groups/{groupId}/settlements
    private static final Pattern SETTLEMENT_PATTERN =
            Pattern.compile("^/api/groups/[a-fA-F0-9\\-]+/settlements/?$");

    @Value("${app.rate-limit.login.limit:5}")
    private int loginLimit;
    @Value("${app.rate-limit.login.window-seconds:60}")
    private int loginWindowSeconds;

    @Value("${app.rate-limit.register.limit:5}")
    private int registerLimit;
    @Value("${app.rate-limit.register.window-seconds:60}")
    private int registerWindowSeconds;

    @Value("${app.rate-limit.expense.limit:30}")
    private int expenseLimit;
    @Value("${app.rate-limit.expense.window-seconds:60}")
    private int expenseWindowSeconds;

    @Value("${app.rate-limit.settlement.limit:10}")
    private int settlementLimit;
    @Value("${app.rate-limit.settlement.window-seconds:60}")
    private int settlementWindowSeconds;

    @org.springframework.beans.factory.annotation.Autowired
    public RateLimitFilter(org.springframework.beans.factory.ObjectProvider<RedisRateLimiter> rateLimiterProvider,
                           org.springframework.beans.factory.ObjectProvider<ClientIpResolver> ipResolverProvider,
                           org.springframework.beans.factory.ObjectProvider<ObjectMapper> objectMapperProvider) {
        this.rateLimiter = rateLimiterProvider.getIfAvailable();
        this.ipResolver = ipResolverProvider.getIfAvailable(ClientIpResolver::new);
        this.objectMapper = objectMapperProvider.getIfAvailable(ObjectMapper::new);
    }

    public RateLimitFilter(RedisRateLimiter rateLimiter,
                           ClientIpResolver ipResolver,
                           ObjectMapper objectMapper) {
        this.rateLimiter = rateLimiter;
        this.ipResolver = ipResolver != null ? ipResolver : new ClientIpResolver();
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
    }

    public RateLimitFilter() {
        this.rateLimiter = null;
        this.ipResolver = new ClientIpResolver();
        this.objectMapper = new ObjectMapper();
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String method = request.getMethod();
        String uri = request.getRequestURI();

        // Only rate-limit specific POST operations
        if ("POST".equalsIgnoreCase(method)) {
            RouteLimit routeLimit = resolveRouteLimit(uri);

            if (routeLimit != null && rateLimiter != null) {
                String clientIp = ipResolver.resolveClientIp(request);
                RateLimitResult result = rateLimiter.checkLimit(
                        routeLimit.keyPrefix(),
                        clientIp,
                        routeLimit.limit(),
                        routeLimit.windowSeconds()
                );

                if (!result.allowed()) {
                    send429Response(response, result.retryAfterSeconds());
                    return;
                }
            }
        }

        filterChain.doFilter(request, response);
    }

    private RouteLimit resolveRouteLimit(String uri) {
        if ("/api/auth/login".equalsIgnoreCase(uri)) {
            return new RouteLimit("rate_limit:login", loginLimit, loginWindowSeconds);
        }
        if ("/api/auth/register".equalsIgnoreCase(uri)) {
            return new RouteLimit("rate_limit:register", registerLimit, registerWindowSeconds);
        }
        if (EXPENSE_PATTERN.matcher(uri).matches()) {
            return new RouteLimit("rate_limit:expense", expenseLimit, expenseWindowSeconds);
        }
        if (SETTLEMENT_PATTERN.matcher(uri).matches()) {
            return new RouteLimit("rate_limit:settlement", settlementLimit, settlementWindowSeconds);
        }
        return null;
    }

    private void send429Response(HttpServletResponse response, long retryAfterSeconds) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("message", "Too many requests. Please try again later.");
        body.put("retryAfter", retryAfterSeconds);

        response.getWriter().write(objectMapper.writeValueAsString(body));
    }

    private record RouteLimit(String keyPrefix, int limit, int windowSeconds) {}
}
