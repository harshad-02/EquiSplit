package com.equisplit.controller;

import com.equisplit.config.SecurityConfig;
import com.equisplit.dto.request.RecordSettlementRequest;
import com.equisplit.dto.response.RecordSettlementResponse;
import com.equisplit.dto.response.RecordedSettlementDto;
import com.equisplit.dto.response.SuggestedSettlementDto;
import com.equisplit.dto.response.SuggestedSettlementsResponse;
import com.equisplit.entity.User;
import com.equisplit.exception.BadRequestException;
import com.equisplit.exception.ConflictException;
import com.equisplit.exception.GlobalExceptionHandler;
import com.equisplit.security.CustomUserDetailsService;
import com.equisplit.security.JwtAuthenticationEntryPoint;
import com.equisplit.security.JwtAuthenticationFilter;
import com.equisplit.security.JwtTokenProvider;
import com.equisplit.security.UserPrincipal;
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

@WebMvcTest(SettlementController.class)
@AutoConfigureMockMvc
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtAuthenticationEntryPoint.class, CookieUtil.class, GlobalExceptionHandler.class})
class SettlementControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private SettlementSuggestionService settlementSuggestionService;

    @MockBean
    private SettlementRecordService settlementRecordService;

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
    @DisplayName("GET /api/groups/{groupId}/settlements/suggested - Returns 200 OK with suggested settlements")
    void testGetSuggestedSettlementsSuccess() throws Exception {
        UUID debtorId = UUID.randomUUID();
        UUID creditorId = UUID.randomUUID();

        SuggestedSettlementDto dto = SuggestedSettlementDto.builder()
                .from(debtorId)
                .to(creditorId)
                .amount("5000")
                .fromName("Bob")
                .toName("Alice")
                .build();

        SuggestedSettlementsResponse response = new SuggestedSettlementsResponse(List.of(dto));

        when(settlementSuggestionService.getSuggestedSettlements(eq(userId), eq(groupId))).thenReturn(response);

        mockMvc.perform(get("/api/groups/" + groupId + "/settlements/suggested")
                        .cookie(authCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.settlements").isArray())
                .andExpect(jsonPath("$.settlements[0].from").value(debtorId.toString()))
                .andExpect(jsonPath("$.settlements[0].to").value(creditorId.toString()))
                .andExpect(jsonPath("$.settlements[0].amount").value("5000"))
                .andExpect(jsonPath("$.settlements[0].fromName").value("Bob"))
                .andExpect(jsonPath("$.settlements[0].toName").value("Alice"))
                .andExpect(jsonPath("$.settlements[0].fromUser").value(debtorId.toString()))
                .andExpect(jsonPath("$.settlements[0].toUser").value(creditorId.toString()));
    }

    @Test
    @DisplayName("GET /api/groups/{groupId}/settlements/suggested - Unauthenticated request returns 401 Unauthorized")
    void testGetSuggestedSettlementsUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/groups/" + groupId + "/settlements/suggested"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /api/groups/{groupId}/settlements - Successfully records settlement returns 201 Created")
    void testRecordSettlementSuccess() throws Exception {
        UUID fromUser = UUID.randomUUID();
        UUID toUser = UUID.randomUUID();
        UUID settlementId = UUID.randomUUID();

        RecordSettlementRequest request = new RecordSettlementRequest(fromUser, toUser, 3000L);
        RecordedSettlementDto dto = new RecordedSettlementDto(
                settlementId, groupId, fromUser, toUser, "3000", Instant.now()
        );
        RecordSettlementResponse responseBody = new RecordSettlementResponse("Settlement recorded successfully", dto);

        when(settlementRecordService.recordSettlement(eq(userId), eq(groupId), eq("test-idem-key"), any(RecordSettlementRequest.class)))
                .thenReturn(SettlementResult.created(responseBody));

        mockMvc.perform(post("/api/groups/" + groupId + "/settlements")
                        .cookie(authCookie)
                        .header("Idempotency-Key", "test-idem-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("Settlement recorded successfully"))
                .andExpect(jsonPath("$.settlement.id").value(settlementId.toString()))
                .andExpect(jsonPath("$.settlement.from_user").value(fromUser.toString()))
                .andExpect(jsonPath("$.settlement.to_user").value(toUser.toString()))
                .andExpect(jsonPath("$.settlement.amount").value("3000"));
    }

    @Test
    @DisplayName("POST /api/groups/{groupId}/settlements - Unauthenticated returns 401 Unauthorized")
    void testRecordSettlementUnauthenticated() throws Exception {
        RecordSettlementRequest request = new RecordSettlementRequest(UUID.randomUUID(), UUID.randomUUID(), 3000L);

        mockMvc.perform(post("/api/groups/" + groupId + "/settlements")
                        .header("Idempotency-Key", "key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /api/groups/{groupId}/settlements - Missing Idempotency-Key returns 400 Bad Request")
    void testRecordSettlementMissingIdempotencyKey() throws Exception {
        RecordSettlementRequest request = new RecordSettlementRequest(UUID.randomUUID(), UUID.randomUUID(), 3000L);

        when(settlementRecordService.recordSettlement(eq(userId), eq(groupId), eq(null), any(RecordSettlementRequest.class)))
                .thenThrow(new BadRequestException("Idempotency-Key header is required"));

        mockMvc.perform(post("/api/groups/" + groupId + "/settlements")
                        .cookie(authCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Idempotency-Key header is required"));
    }

    @Test
    @DisplayName("POST /api/groups/{groupId}/settlements - Reused Idempotency-Key with different payload returns 409 Conflict")
    void testRecordSettlementConflict() throws Exception {
        RecordSettlementRequest request = new RecordSettlementRequest(UUID.randomUUID(), UUID.randomUUID(), 3000L);

        when(settlementRecordService.recordSettlement(eq(userId), eq(groupId), eq("conflict-key"), any(RecordSettlementRequest.class)))
                .thenThrow(new ConflictException("Idempotency-Key has already been used for a different request"));

        mockMvc.perform(post("/api/groups/" + groupId + "/settlements")
                        .cookie(authCookie)
                        .header("Idempotency-Key", "conflict-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Idempotency-Key has already been used for a different request"));
    }
}