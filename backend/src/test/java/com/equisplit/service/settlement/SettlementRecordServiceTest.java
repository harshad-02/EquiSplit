package com.equisplit.service.settlement;

import com.equisplit.dto.request.RecordSettlementRequest;
import com.equisplit.dto.response.RecordSettlementResponse;
import com.equisplit.dto.response.RecordedSettlementDto;
import com.equisplit.entity.*;
import com.equisplit.exception.BadRequestException;
import com.equisplit.exception.ConflictException;
import com.equisplit.exception.ForbiddenException;
import com.equisplit.repository.*;
import com.equisplit.repository.SettlementRepository.PreviousSettlementSummary;
import com.equisplit.service.GroupAuthorizationService;
import com.equisplit.service.balance.GroupBalanceCalculator;
import com.equisplit.service.idempotency.RequestHasher;
import com.equisplit.service.lock.AdvisoryLockService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SettlementRecordServiceTest {

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

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private SettlementRecordService settlementRecordService;

    private UUID requesterId;
    private UUID groupId;
    private UUID debtorId;
    private UUID creditorId;

    private User requester;
    private User debtor;
    private User creditor;
    private Group group;

    @BeforeEach
    void setUp() {
        requesterId = UUID.randomUUID();
        groupId = UUID.randomUUID();
        debtorId = UUID.randomUUID();
        creditorId = UUID.randomUUID();

        requester = new User("Requester", "requester@example.com", "hash", "user");
        requester.setId(requesterId);

        debtor = new User("Debtor", "debtor@example.com", "hash", "user");
        debtor.setId(debtorId);

        creditor = new User("Creditor", "creditor@example.com", "hash", "user");
        creditor.setId(creditorId);

        group = new Group("Test Group", requester);
        group.setId(groupId);
    }

    @Test
    @DisplayName("Missing or blank Idempotency-Key header throws 400 BadRequestException")
    void testMissingIdempotencyKey() {
        RecordSettlementRequest req = new RecordSettlementRequest(debtorId, creditorId, 5000L);

        assertThatThrownBy(() -> settlementRecordService.recordSettlement(requesterId, groupId, null, req))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Idempotency-Key header is required");

        assertThatThrownBy(() -> settlementRecordService.recordSettlement(requesterId, groupId, "   ", req))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Idempotency-Key header is required");
    }

    @Test
    @DisplayName("Idempotency-Key exceeding 255 chars throws 400 BadRequestException")
    void testIdempotencyKeyTooLong() {
        RecordSettlementRequest req = new RecordSettlementRequest(debtorId, creditorId, 5000L);
        String longKey = "k".repeat(256);

        assertThatThrownBy(() -> settlementRecordService.recordSettlement(requesterId, groupId, longKey, req))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Idempotency-Key must not exceed 255 characters");
    }

    @Test
    @DisplayName("Missing payload fields throws 400 BadRequestException")
    void testMissingPayloadFields() {
        assertThatThrownBy(() -> settlementRecordService.recordSettlement(requesterId, groupId, "key-1", null))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("fromUser, toUser and amount are required");

        RecordSettlementRequest reqNoFrom = new RecordSettlementRequest(null, creditorId, 5000L);
        assertThatThrownBy(() -> settlementRecordService.recordSettlement(requesterId, groupId, "key-1", reqNoFrom))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("fromUser, toUser and amount are required");

        RecordSettlementRequest reqNoTo = new RecordSettlementRequest(debtorId, null, 5000L);
        assertThatThrownBy(() -> settlementRecordService.recordSettlement(requesterId, groupId, "key-1", reqNoTo))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("fromUser, toUser and amount are required");

        RecordSettlementRequest reqNoAmount = new RecordSettlementRequest(debtorId, creditorId, null);
        assertThatThrownBy(() -> settlementRecordService.recordSettlement(requesterId, groupId, "key-1", reqNoAmount))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("fromUser, toUser and amount are required");
    }

    @Test
    @DisplayName("Self-settlement (fromUser == toUser) throws 400 BadRequestException")
    void testSelfSettlement() {
        RecordSettlementRequest req = new RecordSettlementRequest(debtorId, debtorId, 5000L);

        assertThatThrownBy(() -> settlementRecordService.recordSettlement(requesterId, groupId, "key-1", req))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("fromUser and toUser must be different");
    }

    @Test
    @DisplayName("Invalid or non-positive amount throws 400 BadRequestException")
    void testInvalidAmount() {
        RecordSettlementRequest zeroAmount = new RecordSettlementRequest(debtorId, creditorId, 0L);
        assertThatThrownBy(() -> settlementRecordService.recordSettlement(requesterId, groupId, "key-1", zeroAmount))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Amount must be greater than zero");

        RecordSettlementRequest negAmount = new RecordSettlementRequest(debtorId, creditorId, -100L);
        assertThatThrownBy(() -> settlementRecordService.recordSettlement(requesterId, groupId, "key-1", negAmount))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Amount must be greater than zero");

        RecordSettlementRequest invalidStr = new RecordSettlementRequest(debtorId, creditorId, "not-a-number");
        assertThatThrownBy(() -> settlementRecordService.recordSettlement(requesterId, groupId, "key-1", invalidStr))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Amount must be a valid integer in paise");
    }

    @Test
    @DisplayName("Idempotency replay returns cached response with matching hash")
    void testIdempotencyReplaySuccess() throws Exception {
        RecordSettlementRequest req = new RecordSettlementRequest(debtorId, creditorId, 5000L);
        when(requestHasher.hashSettlementRequest(debtorId, creditorId, 5000L)).thenReturn("mock-hash-123");

        String cachedBody = "{\"message\":\"Settlement recorded successfully\",\"settlement\":{\"amount\":\"5000\"}}";
        IdempotencyRecord cachedRecord = new IdempotencyRecord(requester, group, "idem-key", "mock-hash-123", 201, cachedBody);

        when(idempotencyKeyRepository.findByUserIdAndGroupIdAndIdempotencyKey(requesterId, groupId, "idem-key"))
                .thenReturn(Optional.of(cachedRecord));

        SettlementResult result = settlementRecordService.recordSettlement(requesterId, groupId, "idem-key", req);

        assertThat(result.statusCode()).isEqualTo(201);
        assertThat(result.responseBody()).isNotNull();

        verify(advisoryLockService).acquireTransactionLock(groupId.toString());
        verify(settlementRepository, never()).save(any());
        verify(auditLogRepository, never()).save(any());
    }

    @Test
    @DisplayName("Reusing Idempotency-Key with different payload throws 409 ConflictException")
    void testIdempotencyKeyConflict() {
        RecordSettlementRequest req = new RecordSettlementRequest(debtorId, creditorId, 5000L);
        when(requestHasher.hashSettlementRequest(debtorId, creditorId, 5000L)).thenReturn("new-hash-456");

        IdempotencyRecord oldRecord = new IdempotencyRecord(requester, group, "idem-key", "old-hash-123", 201, "{}");

        when(idempotencyKeyRepository.findByUserIdAndGroupIdAndIdempotencyKey(requesterId, groupId, "idem-key"))
                .thenReturn(Optional.of(oldRecord));

        assertThatThrownBy(() -> settlementRecordService.recordSettlement(requesterId, groupId, "idem-key", req))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Idempotency-Key has already been used for a different request");

        verify(settlementRepository, never()).save(any());
    }

    @Test
    @DisplayName("Non-member requester throws 403 ForbiddenException")
    void testNonMemberRequesterForbidden() {
        RecordSettlementRequest req = new RecordSettlementRequest(debtorId, creditorId, 5000L);
        when(requestHasher.hashSettlementRequest(debtorId, creditorId, 5000L)).thenReturn("mock-hash");
        when(idempotencyKeyRepository.findByUserIdAndGroupIdAndIdempotencyKey(requesterId, groupId, "key-1"))
                .thenReturn(Optional.empty());

        doThrow(new ForbiddenException("You are not a member of this group"))
                .when(groupAuthService).requireGroupMember(requesterId, groupId);

        assertThatThrownBy(() -> settlementRecordService.recordSettlement(requesterId, groupId, "key-1", req))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("You are not a member of this group");
    }

    @Test
    @DisplayName("Non-member participant throws 400 BadRequestException")
    void testNonMemberParticipant() {
        RecordSettlementRequest req = new RecordSettlementRequest(debtorId, creditorId, 5000L);
        when(requestHasher.hashSettlementRequest(debtorId, creditorId, 5000L)).thenReturn("mock-hash");
        when(idempotencyKeyRepository.findByUserIdAndGroupIdAndIdempotencyKey(requesterId, groupId, "key-1"))
                .thenReturn(Optional.empty());
        when(groupRepository.findById(groupId)).thenReturn(Optional.of(group));

        when(groupMemberRepository.existsByIdGroupIdAndIdUserId(groupId, debtorId)).thenReturn(true);
        when(groupMemberRepository.existsByIdGroupIdAndIdUserId(groupId, creditorId)).thenReturn(false);

        assertThatThrownBy(() -> settlementRecordService.recordSettlement(requesterId, groupId, "key-1", req))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Both users must be members of the group");
    }

    @Test
    @DisplayName("Invalid settlement direction (payer is not debtor, or receiver is not creditor) throws 400")
    void testInvalidSettlementDirection() {
        RecordSettlementRequest req = new RecordSettlementRequest(debtorId, creditorId, 5000L);
        when(requestHasher.hashSettlementRequest(debtorId, creditorId, 5000L)).thenReturn("mock-hash");
        when(idempotencyKeyRepository.findByUserIdAndGroupIdAndIdempotencyKey(requesterId, groupId, "key-1"))
                .thenReturn(Optional.empty());
        when(groupRepository.findById(groupId)).thenReturn(Optional.of(group));
        when(groupMemberRepository.existsByIdGroupIdAndIdUserId(groupId, debtorId)).thenReturn(true);
        when(groupMemberRepository.existsByIdGroupIdAndIdUserId(groupId, creditorId)).thenReturn(true);

        when(expenseRepository.findByGroupIdWithSplitsAndUsersOrderByCreatedAtDesc(groupId)).thenReturn(Collections.emptyList());
        // Both balances are 0
        when(balanceCalculator.calculateFromExpenses(any())).thenReturn(Collections.emptyMap());

        assertThatThrownBy(() -> settlementRecordService.recordSettlement(requesterId, groupId, "key-1", req))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Invalid settlement direction");
    }

    @Test
    @DisplayName("Settlement amount exceeds outstanding debt throws 400 BadRequestException")
    void testSettlementAmountExceedsDebt() {
        RecordSettlementRequest req = new RecordSettlementRequest(debtorId, creditorId, 8000L);
        when(requestHasher.hashSettlementRequest(debtorId, creditorId, 8000L)).thenReturn("mock-hash");
        when(idempotencyKeyRepository.findByUserIdAndGroupIdAndIdempotencyKey(requesterId, groupId, "key-1"))
                .thenReturn(Optional.empty());
        when(groupRepository.findById(groupId)).thenReturn(Optional.of(group));
        when(groupMemberRepository.existsByIdGroupIdAndIdUserId(groupId, debtorId)).thenReturn(true);
        when(groupMemberRepository.existsByIdGroupIdAndIdUserId(groupId, creditorId)).thenReturn(true);

        when(expenseRepository.findByGroupIdWithSplitsAndUsersOrderByCreatedAtDesc(groupId)).thenReturn(Collections.emptyList());
        Map<UUID, Long> balances = new HashMap<>();
        balances.put(debtorId, -5000L);
        balances.put(creditorId, 5000L);
        when(balanceCalculator.calculateFromExpenses(any())).thenReturn(balances);

        PreviousSettlementSummary summary = mock(PreviousSettlementSummary.class);
        when(summary.getPaid_amount()).thenReturn(0L);
        when(summary.getReverse_amount()).thenReturn(0L);
        when(settlementRepository.findPreviousSettlementsBetweenUsers(groupId, debtorId, creditorId))
                .thenReturn(summary);

        assertThatThrownBy(() -> settlementRecordService.recordSettlement(requesterId, groupId, "key-1", req))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Settlement amount exceeds outstanding debt");
    }

    @Test
    @DisplayName("Previous settlements reduce outstanding debt; over-settlement rejected")
    void testPreviousSettlementsReduceOutstandingDebt() {
        RecordSettlementRequest req = new RecordSettlementRequest(debtorId, creditorId, 4000L);
        when(requestHasher.hashSettlementRequest(debtorId, creditorId, 4000L)).thenReturn("mock-hash");
        when(idempotencyKeyRepository.findByUserIdAndGroupIdAndIdempotencyKey(requesterId, groupId, "key-1"))
                .thenReturn(Optional.empty());
        when(groupRepository.findById(groupId)).thenReturn(Optional.of(group));
        when(groupMemberRepository.existsByIdGroupIdAndIdUserId(groupId, debtorId)).thenReturn(true);
        when(groupMemberRepository.existsByIdGroupIdAndIdUserId(groupId, creditorId)).thenReturn(true);

        when(expenseRepository.findByGroupIdWithSplitsAndUsersOrderByCreatedAtDesc(groupId)).thenReturn(Collections.emptyList());
        Map<UUID, Long> balances = new HashMap<>();
        balances.put(debtorId, -5000L);
        balances.put(creditorId, 5000L);
        when(balanceCalculator.calculateFromExpenses(any())).thenReturn(balances);

        // Already paid 3000 out of 5000 debt -> outstanding debt is 2000
        PreviousSettlementSummary summary = mock(PreviousSettlementSummary.class);
        when(summary.getPaid_amount()).thenReturn(3000L);
        when(summary.getReverse_amount()).thenReturn(0L);
        when(settlementRepository.findPreviousSettlementsBetweenUsers(groupId, debtorId, creditorId))
                .thenReturn(summary);

        // Requesting 4000 when only 2000 remains
        assertThatThrownBy(() -> settlementRecordService.recordSettlement(requesterId, groupId, "key-1", req))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Settlement amount exceeds outstanding debt");
    }

    @Test
    @DisplayName("Successful settlement recording creates settlement, audit log, and idempotency key")
    void testSuccessfulSettlementRecording() {
        RecordSettlementRequest req = new RecordSettlementRequest(debtorId, creditorId, 3000L);
        when(requestHasher.hashSettlementRequest(debtorId, creditorId, 3000L)).thenReturn("mock-hash");
        when(idempotencyKeyRepository.findByUserIdAndGroupIdAndIdempotencyKey(requesterId, groupId, "key-1"))
                .thenReturn(Optional.empty());
        when(groupRepository.findById(groupId)).thenReturn(Optional.of(group));
        when(groupMemberRepository.existsByIdGroupIdAndIdUserId(groupId, debtorId)).thenReturn(true);
        when(groupMemberRepository.existsByIdGroupIdAndIdUserId(groupId, creditorId)).thenReturn(true);

        when(expenseRepository.findByGroupIdWithSplitsAndUsersOrderByCreatedAtDesc(groupId)).thenReturn(Collections.emptyList());
        Map<UUID, Long> balances = new HashMap<>();
        balances.put(debtorId, -5000L);
        balances.put(creditorId, 5000L);
        when(balanceCalculator.calculateFromExpenses(any())).thenReturn(balances);

        PreviousSettlementSummary summary = mock(PreviousSettlementSummary.class);
        when(summary.getPaid_amount()).thenReturn(1000L);
        when(summary.getReverse_amount()).thenReturn(0L);
        when(settlementRepository.findPreviousSettlementsBetweenUsers(groupId, debtorId, creditorId))
                .thenReturn(summary);

        when(userRepository.findById(requesterId)).thenReturn(Optional.of(requester));
        when(userRepository.findById(debtorId)).thenReturn(Optional.of(debtor));
        when(userRepository.findById(creditorId)).thenReturn(Optional.of(creditor));

        UUID settlementId = UUID.randomUUID();
        when(settlementRepository.save(any(Settlement.class))).thenAnswer(invocation -> {
            Settlement s = invocation.getArgument(0);
            s.setId(settlementId);
            return s;
        });

        SettlementResult result = settlementRecordService.recordSettlement(requesterId, groupId, "key-1", req);

        assertThat(result.statusCode()).isEqualTo(201);
        assertThat(result.responseBody()).isInstanceOf(RecordSettlementResponse.class);

        RecordSettlementResponse resp = (RecordSettlementResponse) result.responseBody();
        assertThat(resp.getMessage()).isEqualTo("Settlement recorded successfully");
        assertThat(resp.getSettlement().getAmount()).isEqualTo("3000");
        assertThat(resp.getSettlement().getFromUser()).isEqualTo(debtorId);
        assertThat(resp.getSettlement().getToUser()).isEqualTo(creditorId);

        // Verify advisory lock was acquired
        verify(advisoryLockService).acquireTransactionLock(groupId.toString());

        // Verify settlement persisted
        verify(settlementRepository).save(any(Settlement.class));

        // Verify audit log persisted
        verify(auditLogRepository).save(argThat(log ->
                log.getAction().equals("SETTLEMENT_RECORDED") &&
                log.getEntityType().equals("settlement") &&
                log.getEntityId().equals(settlementId) &&
                log.getUser().equals(requester)
        ));

        // Verify idempotency record saved
        verify(idempotencyKeyRepository).save(argThat(idem ->
                idem.getIdempotencyKey().equals("key-1") &&
                idem.getRequestHash().equals("mock-hash") &&
                idem.getResponseStatus() == 201 &&
                idem.getResponseBody().contains("Settlement recorded successfully")
        ));
    }
}
