package com.equisplit.controller;

import com.equisplit.config.SecurityConfig;
import com.equisplit.security.CustomUserDetailsService;
import com.equisplit.security.JwtAuthenticationEntryPoint;
import com.equisplit.security.JwtAuthenticationFilter;
import com.equisplit.security.JwtTokenProvider;
import com.equisplit.util.CookieUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(HealthController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtAuthenticationEntryPoint.class, CookieUtil.class})
class HealthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private JdbcTemplate jdbcTemplate;

    @MockBean
    private JwtTokenProvider tokenProvider;

    @MockBean
    private CustomUserDetailsService userDetailsService;

    @Test
    @DisplayName("GET /api/health returns 200 OK when database is reachable")
    void healthCheckSuccess() throws Exception {
        when(jdbcTemplate.queryForObject(anyString(), eq(String.class)))
                .thenReturn("2026-09-13 08:00:00.000000+00");

        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OK"))
                .andExpect(jsonPath("$.message").value("ExpenseSplitter API is running"))
                .andExpect(jsonPath("$.database").value("Connected"))
                .andExpect(jsonPath("$.time").value("2026-09-13 08:00:00.000000+00"));
    }

    @Test
    @DisplayName("GET /api/health returns 500 when database fails")
    void healthCheckFailure() throws Exception {
        when(jdbcTemplate.queryForObject(anyString(), eq(String.class)))
                .thenThrow(new DataAccessResourceFailureException("Connection refused"));

        mockMvc.perform(get("/api/health"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value("ERROR"))
                .andExpect(jsonPath("$.message").value("Database connection failed"));
    }
}
