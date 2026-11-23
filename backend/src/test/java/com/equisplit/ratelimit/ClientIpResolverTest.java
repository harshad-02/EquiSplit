package com.equisplit.ratelimit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class ClientIpResolverTest {

    private ClientIpResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new ClientIpResolver();
    }

    @Test
    @DisplayName("Returns getRemoteAddr() when X-Forwarded-For header is absent")
    void testDirectRemoteAddr() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("192.168.1.50");

        String ip = resolver.resolveClientIp(request);
        assertThat(ip).isEqualTo("192.168.1.50");
    }

    @Test
    @DisplayName("Returns getRemoteAddr() when X-Forwarded-For header is blank")
    void testBlankXForwardedFor() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.1");
        request.addHeader("X-Forwarded-For", "   ");

        String ip = resolver.resolveClientIp(request);
        assertThat(ip).isEqualTo("10.0.0.1");
    }

    @Test
    @DisplayName("Returns single IP from X-Forwarded-For header")
    void testSingleXForwardedFor() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.1");
        request.addHeader("X-Forwarded-For", "203.0.113.195");

        String ip = resolver.resolveClientIp(request);
        assertThat(ip).isEqualTo("203.0.113.195");
    }

    @Test
    @DisplayName("Returns leftmost client IP when X-Forwarded-For contains multiple proxy hops")
    void testMultiHopXForwardedFor() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.1");
        request.addHeader("X-Forwarded-For", "203.0.113.195, 70.41.3.18, 150.172.238.178");

        String ip = resolver.resolveClientIp(request);
        assertThat(ip).isEqualTo("203.0.113.195");
    }

    @Test
    @DisplayName("Trims whitespace from leftmost IP in X-Forwarded-For")
    void testWhitespaceTrimmedXForwardedFor() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-For", "   198.51.100.25   , 10.0.0.1");

        String ip = resolver.resolveClientIp(request);
        assertThat(ip).isEqualTo("198.51.100.25");
    }
}
