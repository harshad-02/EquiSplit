package com.equisplit.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class JwtTokenProviderTest {

    private JwtTokenProvider tokenProvider;
    private final String secret = "testSecretKeyForJwtTokenProviderTestingThatIsAtLeast32BytesLong!";
    private final long expirationMs = 3600000; // 1 hour

    @BeforeEach
    void setUp() {
        tokenProvider = new JwtTokenProvider();
        ReflectionTestUtils.setField(tokenProvider, "jwtSecret", secret);
        ReflectionTestUtils.setField(tokenProvider, "jwtExpirationMs", expirationMs);
        tokenProvider.init();
    }

    @Test
    @DisplayName("generateToken produces a valid token containing userId and role")
    void testGenerateToken() {
        UUID userId = UUID.randomUUID();
        String role = "user";

        String token = tokenProvider.generateToken(userId, role);

        assertNotNull(token);
        assertTrue(tokenProvider.validateToken(token));
        assertEquals(userId, tokenProvider.getUserIdFromToken(token));
        assertEquals(role, tokenProvider.getRoleFromToken(token));
    }

    @Test
    @DisplayName("validateToken returns false for expired token")
    void testExpiredToken() {
        SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        Date past = new Date(System.currentTimeMillis() - 10000);

        String expiredToken = Jwts.builder()
                .subject(UUID.randomUUID().toString())
                .claim("userId", UUID.randomUUID().toString())
                .claim("role", "user")
                .issuedAt(new Date(past.getTime() - 10000))
                .expiration(past)
                .signWith(key, Jwts.SIG.HS256)
                .compact();

        assertFalse(tokenProvider.validateToken(expiredToken));
    }

    @Test
    @DisplayName("validateToken returns false for malformed token")
    void testMalformedToken() {
        assertFalse(tokenProvider.validateToken("this.is.not.a.valid.jwt.token"));
        assertFalse(tokenProvider.validateToken(""));
        assertFalse(tokenProvider.validateToken(null));
    }

    @Test
    @DisplayName("validateToken returns false for token signed with a different key")
    void testInvalidSignature() {
        String differentSecret = "completelyDifferentSecretKeyForSignatureTestingPurposesOnly!";
        SecretKey differentKey = Keys.hmacShaKeyFor(differentSecret.getBytes(StandardCharsets.UTF_8));

        String foreignToken = Jwts.builder()
                .subject(UUID.randomUUID().toString())
                .claim("userId", UUID.randomUUID().toString())
                .claim("role", "user")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 60000))
                .signWith(differentKey, Jwts.SIG.HS256)
                .compact();

        assertFalse(tokenProvider.validateToken(foreignToken));
    }
}
