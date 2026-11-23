package com.equisplit.controller;

import com.equisplit.config.SecurityConfig;
import com.equisplit.dto.request.CreateExpenseRequest;
import com.equisplit.dto.request.CreateGroupRequest;
import com.equisplit.dto.request.ExpenseSplitRequest;
import com.equisplit.dto.request.RecordSettlementRequest;
import com.equisplit.dto.response.*;
import com.equisplit.entity.User;
import com.equisplit.exception.GlobalExceptionHandler;
import com.equisplit.security.CustomAccessDeniedHandler;
import com.equisplit.security.CustomUserDetailsService;
import com.equisplit.security.JwtAuthenticationEntryPoint;
import com.equisplit.security.JwtAuthenticationFilter;
import com.equisplit.security.JwtTokenProvider;
import com.equisplit.security.UserPrincipal;
import com.equisplit.service.AuthService;
import com.equisplit.service.BalanceService;
import com.equisplit.service.ExpenseService;
import com.equisplit.service.GroupService;
import com.equisplit.service.settlement.SettlementRecordService;
import com.equisplit.service.settlement.SettlementResult;
import com.equisplit.service.settlement.SettlementSuggestionService;
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

@WebMvcTest({
        AuthController.class,
        GroupController.class,
        ExpenseController.class,
        BalanceController.class,
        SettlementController.class
})
@AutoConfigureMockMvc
@Import({
        GlobalExceptionHandler.class,
        SecurityConfig.class,
        JwtAuthenticationFilter.class,
        JwtAuthenticationEntryPoint.class,
        CustomAccessDeniedHandler.class,
        CookieUtil.class
})
class EndToEndSystemIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AuthService authService;

    @MockBean
    private GroupService groupService;

    @MockBean
    private ExpenseService expenseService;

    @MockBean
    private BalanceService balanceService;

    @MockBean
    private SettlementSuggestionService settlementSuggestionService;

    @MockBean
    private SettlementRecordService settlementRecordService;

    @MockBean
    private JwtTokenProvider tokenProvider;

    @MockBean
    private CustomUserDetailsService userDetailsService;

    private UUID aliceId;
    private Cookie authCookie;

    @BeforeEach
    void setUp() {
        aliceId = UUID.randomUUID();
        String token = "mock.auth.jwt.token";
        User user = new User("Alice", "alice@example.com", "hash", "user");
        user.setId(aliceId);
        UserPrincipal principal = UserPrincipal.create(user);

        when(tokenProvider.validateToken(token)).thenReturn(true);
        when(tokenProvider.getUserIdFromToken(token)).thenReturn(aliceId);
        when(userDetailsService.loadUserById(aliceId)).thenReturn(principal);
        authCookie = new Cookie("token", token);
    }

    @Test
    @DisplayName("End-to-End Flow: Group Creation -> Expense -> Balance -> Suggested Settlement -> Settlement Recording -> Idempotent Replay")
    void testCompleteSystemJourney() throws Exception {
        UUID groupId = UUID.randomUUID();
        UUID bobId = UUID.randomUUID();

        // 1. Group Creation
        CreateGroupRequest groupReq = new CreateGroupRequest("Goa Trip");
        GroupDto groupDto = new GroupDto(groupId, "Goa Trip", aliceId, Instant.now());
        when(groupService.createGroup(any(), any())).thenReturn(new CreateGroupResponse("Group created successfully", groupDto));

        mockMvc.perform(post("/api/groups")
                        .cookie(authCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(groupReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.group.name").value("Goa Trip"));

        // 2. Expense Creation (Alice pays 10000, split 5000/5000 with Bob)
        CreateExpenseRequest expenseReq = new CreateExpenseRequest(
                "Beach Resort", 10000L, "equal",
                List.of(new ExpenseSplitRequest(aliceId), new ExpenseSplitRequest(bobId))
        );
        ExpenseDto expenseDto = new ExpenseDto(UUID.randomUUID(), groupId, aliceId, "Beach Resort", 10000L, "equal", Instant.now());
        List<ExpenseSplitDto> splitDtos = List.of(
                new ExpenseSplitDto(aliceId, 5000L, null, "Alice"),
                new ExpenseSplitDto(bobId, 5000L, null, "Bob")
        );
        when(expenseService.createExpense(any(), eq(groupId), any()))
                .thenReturn(new CreateExpenseResponse("Expense created successfully", expenseDto, splitDtos));

        mockMvc.perform(post("/api/groups/" + groupId + "/expenses")
                        .cookie(authCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(expenseReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("Expense created successfully"));

        // 3. Balance Calculation (Alice +5000, Bob -5000)
        List<UserBalanceDto> userBalances = List.of(
                new UserBalanceDto(aliceId, "Alice", "alice@example.com", 5000L),
                new UserBalanceDto(bobId, "Bob", "bob@example.com", -5000L)
        );
        when(balanceService.getGroupBalances(any(), eq(groupId)))
                .thenReturn(new GroupBalancesResponse(userBalances));

        mockMvc.perform(get("/api/groups/" + groupId + "/balances")
                        .cookie(authCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balances[0].balance").value(5000))
                .andExpect(jsonPath("$.balances[1].balance").value(-5000));

        // 4. Suggested Settlement (Bob -> Alice 5000)
        SuggestedSettlementDto suggestion = new SuggestedSettlementDto(bobId, aliceId, "5000", "Bob", "Alice");
        when(settlementSuggestionService.getSuggestedSettlements(any(), eq(groupId)))
                .thenReturn(new SuggestedSettlementsResponse(List.of(suggestion)));

        mockMvc.perform(get("/api/groups/" + groupId + "/settlements/suggested")
                        .cookie(authCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.settlements[0].amount").value("5000"))
                .andExpect(jsonPath("$.settlements[0].from").value(bobId.toString()))
                .andExpect(jsonPath("$.settlements[0].to").value(aliceId.toString()));

        // 5. Settlement Recording (Bob pays Alice 5000 with Idempotency-Key)
        RecordSettlementRequest settleReq = new RecordSettlementRequest(bobId, aliceId, 5000L);
        RecordedSettlementDto recordedDto = new RecordedSettlementDto(
                UUID.randomUUID(), groupId, bobId, aliceId, "5000", Instant.now()
        );
        RecordSettlementResponse settleResp = new RecordSettlementResponse("Settlement recorded successfully", recordedDto);
        when(settlementRecordService.recordSettlement(any(), eq(groupId), eq("trip-settle-1"), any()))
                .thenReturn(SettlementResult.created(settleResp));

        mockMvc.perform(post("/api/groups/" + groupId + "/settlements")
                        .cookie(authCookie)
                        .header("Idempotency-Key", "trip-settle-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(settleReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.settlement.amount").value("5000"));

        // 6. Idempotent Replay (Re-send with same Idempotency-Key)
        when(settlementRecordService.recordSettlement(any(), eq(groupId), eq("trip-settle-1"), any()))
                .thenReturn(SettlementResult.replay(201, settleResp));

        mockMvc.perform(post("/api/groups/" + groupId + "/settlements")
                        .cookie(authCookie)
                        .header("Idempotency-Key", "trip-settle-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(settleReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("Settlement recorded successfully"));
    }
}
