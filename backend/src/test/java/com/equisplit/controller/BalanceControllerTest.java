package com.equisplit.controller;

import com.equisplit.config.SecurityConfig;
import com.equisplit.dto.response.GroupBalancesResponse;
import com.equisplit.dto.response.UserBalanceDto;
import com.equisplit.entity.User;
import com.equisplit.exception.GlobalExceptionHandler;
import com.equisplit.security.CustomUserDetailsService;
import com.equisplit.security.JwtAuthenticationEntryPoint;
import com.equisplit.security.JwtAuthenticationFilter;
import com.equisplit.security.JwtTokenProvider;
import com.equisplit.security.UserPrincipal;
import com.equisplit.service.BalanceService;
import com.equisplit.util.CookieUtil;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(BalanceController.class)
@AutoConfigureMockMvc
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtAuthenticationEntryPoint.class, CookieUtil.class, GlobalExceptionHandler.class})
class BalanceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private BalanceService balanceService;

    @MockBean
    private JwtTokenProvider tokenProvider;

    @MockBean
    private CustomUserDetailsService userDetailsService;

    private UUID userId;
    private UUID groupId;
    private Cookie authCookie;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        groupId = UUID.randomUUID();
        String token = "valid-mock-jwt-token";

        authCookie = new Cookie("token", token);

        User mockUser = new User("Alice", "alice@example.com", "hashed", "user");
        mockUser.setId(userId);
        UserPrincipal principal = UserPrincipal.create(mockUser);

        when(tokenProvider.validateToken(token)).thenReturn(true);
        when(tokenProvider.getUserIdFromToken(token)).thenReturn(userId);
        when(userDetailsService.loadUserById(userId)).thenReturn(principal);
    }

    @Test
    @DisplayName("GET /api/groups/{groupId}/balances - Returns 200 OK with calculated balances")
    void testGetGroupBalancesSuccess() throws Exception {
        List<UserBalanceDto> balances = List.of(
                new UserBalanceDto(userId, "Alice", "alice@example.com", 5000L),
                new UserBalanceDto(UUID.randomUUID(), "Bob", "bob@example.com", -5000L)
        );
        GroupBalancesResponse response = new GroupBalancesResponse(balances);

        when(balanceService.getGroupBalances(eq(userId), eq(groupId))).thenReturn(response);

        mockMvc.perform(get("/api/groups/" + groupId + "/balances")
                        .cookie(authCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balances").isArray())
                .andExpect(jsonPath("$.balances[0].name").value("Alice"))
                .andExpect(jsonPath("$.balances[0].balance").value("5000"))
                .andExpect(jsonPath("$.balances[0].user_id").value(userId.toString()))
                .andExpect(jsonPath("$.balances[1].name").value("Bob"))
                .andExpect(jsonPath("$.balances[1].balance").value("-5000"));
    }

    @Test
    @DisplayName("GET /api/groups/{groupId}/balances - Unauthenticated request returns 401 Unauthorized")
    void testGetGroupBalancesUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/groups/" + groupId + "/balances"))
                .andExpect(status().isUnauthorized());
    }
}
