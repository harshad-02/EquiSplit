package com.equisplit.service;

import com.equisplit.dto.request.RecordSettlementRequest;
import com.equisplit.entity.Group;
import com.equisplit.entity.User;
import com.equisplit.repository.*;
import com.equisplit.service.balance.GroupBalanceCalculator;
import com.equisplit.service.idempotency.RequestHasher;
import com.equisplit.service.lock.AdvisoryLockService;
import com.equisplit.service.settlement.SettlementRecordService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransactionRollbackTest {

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
    @Mock
    private GroupBalanceCalculator balanceCalculator;
    @Mock
    private ObjectMapper objectMapper;

    private SettlementRecordService settlementRecordService;

    private UUID groupId;
    private UUID aliceId;
    private UUID bobId;
    private User alice;
    private User bob;
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
        alice.setId(aliceId);
        bob = new User("Bob", "bob@example.com", "hash", "user");
        bob.setId(bobId);
        group = new Group("Trip", alice);
        group.setId(groupId);

        lenient().when(groupRepository.findById(groupId)).thenReturn(Optional.of(group));
        lenient().when(userRepository.findById(aliceId)).thenReturn(Optional.of(alice));
        lenient().when(userRepository.findById(bobId)).thenReturn(Optional.of(bob));
        lenient().when(groupMemberRepository.existsByIdGroupIdAndIdUserId(groupId, aliceId)).thenReturn(true);
        lenient().when(groupMemberRepository.existsByIdGroupIdAndIdUserId(groupId, bobId)).thenReturn(true);
        lenient().when(idempotencyKeyRepository.findByUserIdAndGroupIdAndIdempotencyKey(any(), any(), any()))
                .thenReturn(Optional.empty());
        lenient().when(requestHasher.hashSettlementRequest(any(), any(), any()))
                .thenReturn("mockhash");

        lenient().when(expenseRepository.findByGroupIdWithSplitsAndUsersOrderByCreatedAtDesc(groupId))
                .thenReturn(List.of());
        java.util.Map<UUID, Long> balances = new java.util.HashMap<>();
        balances.put(aliceId, -5000L);
        balances.put(bobId, 5000L);
        lenient().when(balanceCalculator.calculateFromExpenses(any())).thenReturn(balances);

        SettlementRepository.PreviousSettlementSummary summary = mock(SettlementRepository.PreviousSettlementSummary.class);
        lenient().when(summary.getPaid_amount()).thenReturn(0L);
        lenient().when(summary.getReverse_amount()).thenReturn(0L);
        lenient().when(settlementRepository.findPreviousSettlementsBetweenUsers(eq(groupId), eq(aliceId), eq(bobId)))
                .thenReturn(summary);
        lenient().when(settlementRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    @DisplayName("Transaction Rollback: Failure during idempotency serialization aborts transaction and does not persist idempotency key")
    void testFailureDuringIdempotencyPersistenceRollsBack() throws Exception {
        when(objectMapper.writeValueAsString(any())).thenThrow(new JsonProcessingException("Serialization failed") {});

        RecordSettlementRequest req = new RecordSettlementRequest(aliceId, bobId, 5000L);

        assertThatThrownBy(() ->
                settlementRecordService.recordSettlement(aliceId, groupId, "key-fail-1", req)
        ).isInstanceOf(IllegalStateException.class)
         .hasMessageContaining("Failed to serialize settlement response");

        // Verify that the idempotency key was NOT saved
        verify(idempotencyKeyRepository, never()).save(any());
    }

    @Test
    @DisplayName("Transaction Rollback: Failure during audit log persistence aborts transaction and does not save idempotency record")
    void testFailureDuringAuditLogPersistenceRollsBack() {
        doThrow(new RuntimeException("Database error writing audit log"))
                .when(auditLogRepository).save(any());

        RecordSettlementRequest req = new RecordSettlementRequest(aliceId, bobId, 5000L);

        assertThatThrownBy(() ->
                settlementRecordService.recordSettlement(aliceId, groupId, "key-fail-2", req)
        ).isInstanceOf(RuntimeException.class)
         .hasMessageContaining("Database error writing audit log");

        // Verify that subsequent save steps were halted
        verify(idempotencyKeyRepository, never()).save(any());
    }
}
