package com.equisplit.controller;

import com.equisplit.config.SecurityConfig;
import com.equisplit.dto.request.CreateExpenseRequest;
import com.equisplit.dto.request.ExpenseSplitRequest;
import com.equisplit.dto.response.CreateExpenseResponse;
import com.equisplit.dto.response.ExpenseDetailDto;
import com.equisplit.dto.response.ExpenseDto;
import com.equisplit.dto.response.ExpenseSplitDto;
import com.equisplit.dto.response.GroupExpensesResponse;
import com.equisplit.entity.User;
import com.equisplit.exception.GlobalExceptionHandler;
import com.equisplit.security.CustomUserDetailsService;
import com.equisplit.security.JwtAuthenticationEntryPoint;
import com.equisplit.security.JwtAuthenticationFilter;
import com.equisplit.security.JwtTokenProvider;
import com.equisplit.security.UserPrincipal;
import com.equisplit.service.ExpenseService;
import com.equisplit.util.CookieUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ExpenseController.class)
@AutoConfigureMockMvc
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtAuthenticationEntryPoint.class, CookieUtil.class, GlobalExceptionHandler.class})
class ExpenseControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ExpenseService expenseService;

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
    @DisplayName("POST /api/groups/{groupId}/expenses - Successfully creates expense (201)")
    void testCreateGroupExpenseSuccess() throws Exception {
        CreateExpenseRequest request = new CreateExpenseRequest(
                "Lunch with team", 10000L, "equal",
                List.of(new ExpenseSplitRequest(userId))
        );

        ExpenseDto expenseDto = new ExpenseDto(
                UUID.randomUUID(), groupId, userId, "Lunch with team", 10000L, "equal", Instant.now()
        );
        List<ExpenseSplitDto> splitDtos = List.of(new ExpenseSplitDto(userId, 10000L));
        CreateExpenseResponse response = new CreateExpenseResponse("Expense created successfully", expenseDto, splitDtos);

        when(expenseService.createExpense(eq(userId), eq(groupId), any(CreateExpenseRequest.class)))
                .thenReturn(response);

        mockMvc.perform(post("/api/groups/" + groupId + "/expenses")
                        .cookie(authCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("Expense created successfully"))
                .andExpect(jsonPath("$.expense.description").value("Lunch with team"))
                .andExpect(jsonPath("$.expense.total_amount").value("10000"))
                .andExpect(jsonPath("$.splits[0].amount").value(10000));
    }

    @Test
    @DisplayName("POST /api/expenses - Successfully creates expense with groupId in body (201)")
    void testCreateExpenseWithBodyGroupIdSuccess() throws Exception {
        CreateExpenseRequest request = new CreateExpenseRequest(
                groupId, "Cab Ride", 5000L, "equal",
                List.of(new ExpenseSplitRequest(userId))
        );

        ExpenseDto expenseDto = new ExpenseDto(
                UUID.randomUUID(), groupId, userId, "Cab Ride", 5000L, "equal", Instant.now()
        );
        List<ExpenseSplitDto> splitDtos = List.of(new ExpenseSplitDto(userId, 5000L));
        CreateExpenseResponse response = new CreateExpenseResponse("Expense created successfully", expenseDto, splitDtos);

        when(expenseService.createExpense(eq(userId), eq(null), any(CreateExpenseRequest.class)))
                .thenReturn(response);

        mockMvc.perform(post("/api/expenses")
                        .cookie(authCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("Expense created successfully"))
                .andExpect(jsonPath("$.expense.description").value("Cab Ride"));
    }

    @Test
    @DisplayName("POST /api/groups/{groupId}/expenses - Validation error when description is blank (400)")
    void testCreateGroupExpenseValidationFailure() throws Exception {
        CreateExpenseRequest request = new CreateExpenseRequest(
                "", 10000L, "equal",
                List.of(new ExpenseSplitRequest(userId))
        );

        mockMvc.perform(post("/api/groups/" + groupId + "/expenses")
                        .cookie(authCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/groups/{groupId}/expenses - Unauthenticated request returns 401")
    void testCreateGroupExpenseUnauthenticated() throws Exception {
        CreateExpenseRequest request = new CreateExpenseRequest(
                "Lunch", 10000L, "equal",
                List.of(new ExpenseSplitRequest(userId))
        );

        mockMvc.perform(post("/api/groups/" + groupId + "/expenses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/groups/{groupId}/expenses - Successfully returns group expenses (200)")
    void testGetGroupExpensesSuccess() throws Exception {
        List<ExpenseDetailDto> expenseList = List.of(
                new ExpenseDetailDto(UUID.randomUUID(), "Lunch", 10000L, "equal",
                        userId, "Alice", Instant.now(), List.of(new ExpenseSplitDto(userId, 10000L)))
        );
        GroupExpensesResponse response = new GroupExpensesResponse(expenseList);

        when(expenseService.getGroupExpenses(eq(userId), eq(groupId))).thenReturn(response);

        mockMvc.perform(get("/api/groups/" + groupId + "/expenses")
                        .cookie(authCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expenses").isArray())
                .andExpect(jsonPath("$.expenses[0].description").value("Lunch"))
                .andExpect(jsonPath("$.expenses[0].paid_by_name").value("Alice"));
    }

    @Test
    @DisplayName("GET /api/groups/{groupId}/expenses - Unauthenticated request returns 401")
    void testGetGroupExpensesUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/groups/" + groupId + "/expenses"))
                .andExpect(status().isUnauthorized());
    }
}
