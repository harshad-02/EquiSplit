package com.equisplit.service;

import com.equisplit.dto.request.RecordSettlementRequest;
import com.equisplit.entity.Group;
import com.equisplit.entity.GroupMember;
import com.equisplit.entity.User;
import com.equisplit.exception.BadRequestException;
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

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SettlementConcurrencyTest {

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
        lenient().when(settlementRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(idempotencyKeyRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(auditLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    @DisplayName("Concurrency: When two settlement requests race for the exact same outstanding debt, only one succeeds and over-settlement is prevented")
    void testConcurrentSettlementRacePreventsOverSettlement() throws Exception {
        // Alice owes Bob 10000 paise
        // First query in thread 1 sees 0 previous settlements (outstanding debt 10000)
        // Thread 2 sees 10000 previous settlements already paid (outstanding debt 0)
        SettlementRepository.PreviousSettlementSummary zeroPaid = new SettlementRepository.PreviousSettlementSummary() {
            @Override public Long getPaid_amount() { return 0L; }
            @Override public Long getReverse_amount() { return 0L; }
        };

        SettlementRepository.PreviousSettlementSummary fullPaid = new SettlementRepository.PreviousSettlementSummary() {
            @Override public Long getPaid_amount() { return 10000L; }
            @Override public Long getReverse_amount() { return 0L; }
        };

        // We simulate the database state under serialization:
        // First transaction finds no previous settlements.
        // Second transaction finds previous settlement already recorded.
        when(settlementRepository.findPreviousSettlementsBetweenUsers(eq(groupId), eq(aliceId), eq(bobId)))
                .thenReturn(zeroPaid)
                .thenReturn(fullPaid);

        // Expense state: Alice owes 10000, Bob is owed 10000
        com.equisplit.entity.Expense mockExpense = new com.equisplit.entity.Expense(group, bob, "Dinner", 20000L, "equal");
        com.equisplit.entity.ExpenseSplit splitAlice = new com.equisplit.entity.ExpenseSplit(mockExpense, alice, 10000L, null);
        com.equisplit.entity.ExpenseSplit splitBob = new com.equisplit.entity.ExpenseSplit(mockExpense, bob, 10000L, null);
        mockExpense.addSplit(splitAlice);
        mockExpense.addSplit(splitBob);

        when(expenseRepository.findByGroupIdWithSplitsAndUsersOrderByCreatedAtDesc(groupId))
                .thenReturn(List.of(mockExpense));

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger rejectedCount = new AtomicInteger(0);

        RecordSettlementRequest req = new RecordSettlementRequest(aliceId, bobId, 10000L);

        Callable<Void> task = () -> {
            startLatch.await();
            try {
                SettlementResult result = settlementRecordService.recordSettlement(
                        aliceId, groupId, "key-" + UUID.randomUUID(), req
                );
                if (result.statusCode() == 201) {
                    successCount.incrementAndGet();
                }
            } catch (BadRequestException e) {
                if (e.getMessage().contains("No outstanding debt exists")) {
                    rejectedCount.incrementAndGet();
                }
            }
            return null;
        };

        Future<Void> f1 = executor.submit(task);
        Future<Void> f2 = executor.submit(task);

        startLatch.countDown();
        f1.get(5, TimeUnit.SECONDS);
        f2.get(5, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(successCount.get()).isEqualTo(1);
        assertThat(rejectedCount.get()).isEqualTo(1);
        // Advisory lock acquired once per transaction
        verify(advisoryLockService, times(2)).acquireTransactionLock(groupId.toString());
        // Settlement saved exactly once
        verify(settlementRepository, times(1)).save(any());
    }
}
