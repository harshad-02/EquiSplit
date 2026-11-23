package com.equisplit.controller;

import com.equisplit.config.SecurityConfig;
import com.equisplit.dto.request.CreateExpenseRequest;
import com.equisplit.dto.request.CreateGroupRequest;
import com.equisplit.dto.request.ExpenseSplitRequest;
import com.equisplit.dto.request.LoginRequest;
import com.equisplit.dto.request.RegisterRequest;
import com.equisplit.dto.response.ExpenseSplitDto;
import com.equisplit.exception.GlobalExceptionHandler;
import com.equisplit.security.CustomAccessDeniedHandler;
import com.equisplit.security.CustomUserDetailsService;
import com.equisplit.security.JwtAuthenticationEntryPoint;
import com.equisplit.security.JwtAuthenticationFilter;
import com.equisplit.security.JwtTokenProvider;
import com.equisplit.service.AuthService;
import com.equisplit.service.ExpenseService;
import com.equisplit.service.GroupService;
import com.equisplit.util.CookieUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({AuthController.class, ExpenseController.class, GroupController.class})
@AutoConfigureMockMvc(addFilters = false)
@Import({GlobalExceptionHandler.class, SecurityConfig.class, JwtAuthenticationFilter.class, JwtAuthenticationEntryPoint.class, CustomAccessDeniedHandler.class, CookieUtil.class})
class ValidationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AuthService authService;

    @MockBean
    private ExpenseService expenseService;

    @MockBean
    private GroupService groupService;

    @MockBean
    private JwtTokenProvider tokenProvider;

    @MockBean
    private CustomUserDetailsService userDetailsService;

    @Test
    @DisplayName("Register with blank name, invalid email, and short password returns 400 with multiple field errors")
    void testRegisterMultipleValidationErrors() throws Exception {
        RegisterRequest request = new RegisterRequest("", "not-an-email", "123");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", notNullValue()))
                .andExpect(jsonPath("$.errors.email", notNullValue()))
                .andExpect(jsonPath("$.errors.password", notNullValue()))
                .andExpect(jsonPath("$.stackTrace").doesNotExist())
                .andExpect(jsonPath("$.trace").doesNotExist());
    }

    @Test
    @DisplayName("Login with blank credentials returns 400 with field errors")
    void testLoginValidationErrors() throws Exception {
        LoginRequest request = new LoginRequest("", "");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", notNullValue()))
                .andExpect(jsonPath("$.errors.email", notNullValue()))
                .andExpect(jsonPath("$.errors.password", notNullValue()));
    }

    @Test
    @DisplayName("CreateGroup with blank name returns 400")
    void testCreateGroupValidation() throws Exception {
        CreateGroupRequest request = new CreateGroupRequest("");

        mockMvc.perform(post("/api/groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", notNullValue()))
                .andExpect(jsonPath("$.errors.name", notNullValue()));
    }

    @Test
    @DisplayName("CreateExpense with negative amount returns 400")
    void testCreateExpenseNegativeAmount() throws Exception {
        UUID groupId = UUID.randomUUID();
        CreateExpenseRequest request = new CreateExpenseRequest(
                "Dinner",
                -500L,
                "equal",
                List.of(new ExpenseSplitRequest(UUID.randomUUID()))
        );

        mockMvc.perform(post("/api/groups/" + groupId + "/expenses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", notNullValue()))
                .andExpect(jsonPath("$.errors.totalAmount", notNullValue()));
    }

    @Test
    @DisplayName("CreateExpense with empty splits returns 400")
    void testCreateExpenseEmptySplits() throws Exception {
        UUID groupId = UUID.randomUUID();
        CreateExpenseRequest request = new CreateExpenseRequest(
                "Dinner",
                1000L,
                "equal",
                Collections.emptyList()
        );

        mockMvc.perform(post("/api/groups/" + groupId + "/expenses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", notNullValue()))
                .andExpect(jsonPath("$.errors.splits", notNullValue()));
    }

    @Test
    @DisplayName("Malformed JSON payload returns 400 without exposing parser stack trace")
    void testMalformedJsonPayload() throws Exception {
        String malformedJson = "{ \"name\": ";

        mockMvc.perform(post("/api/groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(malformedJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid or malformed JSON request"))
                .andExpect(jsonPath("$.stackTrace").doesNotExist())
                .andExpect(jsonPath("$.trace").doesNotExist());
    }

    @Test
    @DisplayName("Invalid UUID path variable returns 400 with clean error")
    void testInvalidUuidPathVariable() throws Exception {
        mockMvc.perform(get("/api/groups/not-a-valid-uuid/expenses"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Invalid parameter: groupId")))
                .andExpect(jsonPath("$.stackTrace").doesNotExist());
    }
}
