package com.equisplit.ratelimit;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

@Component
public class ClientIpResolver {

    /**
     * Resolves client IP address from X-Forwarded-For header or getRemoteAddr().
     * In proxy setups (Render, Vercel, Nginx), the leftmost IP in X-Forwarded-For
     * represents the original client address.
     */
    public String resolveClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.trim().isEmpty()) {
            String[] ips = xForwardedFor.split(",");
            if (ips.length > 0) {
                String firstIp = ips[0].trim();
                if (!firstIp.isEmpty()) {
                    return firstIp;
                }
            }
        }
        return request.getRemoteAddr() != null ? request.getRemoteAddr() : "127.0.0.1";
    }
}
