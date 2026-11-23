package com.equisplit.util;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Optional;

@Component
public class CookieUtil {

    @Value("${app.cookie.name:token}")
    private String cookieName;

    @Value("${app.cookie.secure:false}")
    private boolean secure;

    @Value("${app.cookie.same-site:lax}")
    private String sameSite;

    public Optional<String> extractTokenFromCookie(HttpServletRequest request) {
        if (request.getCookies() == null) {
            return Optional.empty();
        }

        return Arrays.stream(request.getCookies())
                .filter(cookie -> cookieName.equals(cookie.getName()))
                .map(Cookie::getValue)
                .filter(val -> val != null && !val.isBlank())
                .findFirst();
    }

    public void addAuthCookie(HttpServletResponse response, String token, long maxAgeSeconds) {
        ResponseCookie cookie = ResponseCookie.from(cookieName, token)
                .httpOnly(true)
                .secure(secure)
                .path("/")
                .maxAge(maxAgeSeconds)
                .sameSite(sameSite)
                .build();

        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    public void clearAuthCookie(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from(cookieName, "")
                .httpOnly(true)
                .secure(secure)
                .path("/")
                .maxAge(0)
                .sameSite(sameSite)
                .build();

        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    public String getCookieName() {
        return cookieName;
    }
}
