package com.equisplit.service;

import com.equisplit.dto.request.RecordSettlementRequest;
import com.equisplit.entity.Group;
import com.equisplit.entity.IdempotencyRecord;
import com.equisplit.entity.User;
import com.equisplit.exception.ConflictException;
import com.equisplit.repository.*;
import com.equisplit.service.balance.GroupBalanceCalculator;
import com.equisplit.service.idempotency.RequestHasher;
import com.equisplit.service.lock.AdvisoryLockService;
import com.equisplit.service.settlement.SettlementRecordService;
import com.equisplit.service.settlement.SettlementResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IdempotencyComprehensiveTest {

    @Mock
    private GroupAuthorizationService groupAuthService;
    @Mock
    private AdvisoryLockService advisoryLockService;
    @Mock
    private RequestHasher requestHasher;
    @Mock
    private ExpenseRepository expenseRepository;
    @Mock
    private SettlementRepository settlementRepository;
    @Mock
    private IdempotencyKeyRepository idempotencyKeyRepository;
    @Mock
    private AuditLogRepository auditLogRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private GroupRepository groupRepository;
    @Mock
    private GroupMemberRepository groupMemberRepository;
    @Spy
    private GroupBalanceCalculator balanceCalculator = new GroupBalanceCalculator();
    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    private SettlementRecordService settlementRecordService;

    private UUID groupId;
    private UUID aliceId;
    private UUID bobId;
    private User alice;
    private Group group;

    @BeforeEach
    void setUp() {
        settlementRecordService = new SettlementRecordService(
                groupAuthService,
                advisoryLockService,
                requestHasher,
                expenseRepository,
                settlementRepository,
                idempotencyKeyRepository,
                auditLogRepository,
                userRepository,
                groupRepository,
                groupMemberRepository,
                balanceCalculator,
                objectMapper
        );

        groupId = UUID.randomUUID();
        aliceId = UUID.randomUUID();
        bobId = UUID.randomUUID();

        alice = new User("Alice", "alice@example.com", "hash", "user");
        group = new Group("Trip", alice);
    }

    @Test
    @DisplayName("Idempotency Replay: Same key and matching hash returns cached replay body without new settlement save")
    void testIdempotentReplayReturnsCachedResponse() {
        String key = "idemp-key-100";
        String hash = "hash123";
        String cachedJson = "{\"message\":\"Settlement recorded successfully\",\"settlement\":{\"amount\":\"5000\"}}";

        when(requestHasher.hashSettlementRequest(aliceId, bobId, 5000L)).thenReturn(hash);

        IdempotencyRecord cachedRecord = new IdempotencyRecord(
                alice, group, key, hash, 201, cachedJson
        );
        when(idempotencyKeyRepository.findByUserIdAndGroupIdAndIdempotencyKey(aliceId, groupId, key))
                .thenReturn(Optional.of(cachedRecord));

        RecordSettlementRequest req = new RecordSettlementRequest(aliceId, bobId, 5000L);
        SettlementResult result = settlementRecordService.recordSettlement(aliceId, groupId, key, req);

        assertThat(result.statusCode()).isEqualTo(201);
        assertThat(result.responseBody()).isNotNull();
        // Crucial: No new database writes occurred
        verify(settlementRepository, never()).save(any());
        verify(auditLogRepository, never()).save(any());
    }

    @Test
    @DisplayName("Idempotency Conflict: Same key with different hash throws 409 Conflict")
    void testIdempotencyKeyReuseWithDifferentPayloadThrows409() {
        String key = "idemp-key-100";
        String cachedHash = "originalHash";
        String mutatedHash = "mutatedHash";

        when(requestHasher.hashSettlementRequest(aliceId, bobId, 8000L)).thenReturn(mutatedHash);

        IdempotencyRecord cachedRecord = new IdempotencyRecord(
                alice, group, key, cachedHash, 201, "{}"
        );
        when(idempotencyKeyRepository.findByUserIdAndGroupIdAndIdempotencyKey(aliceId, groupId, key))
                .thenReturn(Optional.of(cachedRecord));

        RecordSettlementRequest mutatedReq = new RecordSettlementRequest(aliceId, bobId, 8000L);

        assertThatThrownBy(() ->
                settlementRecordService.recordSettlement(aliceId, groupId, key, mutatedReq)
        ).isInstanceOf(ConflictException.class)
         .hasMessageContaining("Idempotency-Key has already been used for a different request");

        verify(settlementRepository, never()).save(any());
    }

    @Test
    @DisplayName("Idempotency Scope: Query uses composite (userId, groupId, idempotencyKey) ensuring isolation across groups")
    void testIdempotencyQueryScopedByGroupAndUser() {
        String key = "shared-key-123";
        RecordSettlementRequest req = new RecordSettlementRequest(aliceId, bobId, 5000L);

        when(idempotencyKeyRepository.findByUserIdAndGroupIdAndIdempotencyKey(aliceId, groupId, key))
                .thenReturn(Optional.empty());

        try {
            settlementRecordService.recordSettlement(aliceId, groupId, key, req);
        } catch (Exception ignored) {
            // Expected to proceed past idempotency check
        }

        verify(idempotencyKeyRepository).findByUserIdAndGroupIdAndIdempotencyKey(aliceId, groupId, key);
    }
}
