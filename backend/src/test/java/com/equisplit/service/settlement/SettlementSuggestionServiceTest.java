package com.equisplit.service.settlement;

import com.equisplit.dto.response.SuggestedSettlementDto;
import com.equisplit.dto.response.SuggestedSettlementsResponse;
import com.equisplit.entity.Expense;
import com.equisplit.entity.ExpenseSplit;
import com.equisplit.entity.Settlement;
import com.equisplit.entity.User;
import com.equisplit.exception.ForbiddenException;
import com.equisplit.repository.ExpenseRepository;
import com.equisplit.repository.SettlementRepository;
import com.equisplit.repository.UserRepository;
import com.equisplit.service.GroupAuthorizationService;
import com.equisplit.service.balance.GroupBalanceCalculator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SettlementSuggestionServiceTest {

    @Mock
    private GroupAuthorizationService groupAuthorizationService;

    @Mock
    private ExpenseRepository expenseRepository;

    @Mock
    private SettlementRepository settlementRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private GroupBalanceCalculator balanceCalculator;

    @Mock
    private DebtSettlementCalculator debtSettlementCalculator;

    @InjectMocks
    private SettlementSuggestionService settlementSuggestionService;

    private UUID requesterId;
    private UUID groupId;
    private UUID aliceId;
    private UUID bobId;

    private User alice;
    private User bob;

    @BeforeEach
    void setUp() {
        requesterId = UUID.randomUUID();
        groupId = UUID.randomUUID();
        aliceId = UUID.randomUUID();
        bobId = UUID.randomUUID();

        alice = new User("Alice", "alice@example.com", "hash", "user");
        alice.setId(aliceId);
        bob = new User("Bob", "bob@example.com", "hash", "user");
        bob.setId(bobId);
    }

    @Test
    @DisplayName("Non-member requester throws ForbiddenException")
    void testNonMemberForbidden() {
        doThrow(new ForbiddenException("You are not a member of this group"))
                .when(groupAuthorizationService).requireGroupMember(requesterId, groupId);

        assertThatThrownBy(() -> settlementSuggestionService.getSuggestedSettlements(requesterId, groupId))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("You are not a member of this group");

        verify(expenseRepository, never()).findByGroupIdWithSplitsAndUsersOrderByCreatedAtDesc(any());
    }

    @Test
    @DisplayName("Empty expenses and settlements returns empty response")
    void testEmptyExpensesReturnsEmpty() {
        when(expenseRepository.findByGroupIdWithSplitsAndUsersOrderByCreatedAtDesc(groupId))
                .thenReturn(Collections.emptyList());
        when(settlementRepository.findByGroupId(groupId))
                .thenReturn(Collections.emptyList());
        when(balanceCalculator.calculateFromExpenses(any())).thenReturn(Collections.emptyMap());
        when(debtSettlementCalculator.calculateSettlements(any())).thenReturn(Collections.emptyList());

        SuggestedSettlementsResponse response = settlementSuggestionService.getSuggestedSettlements(requesterId, groupId);

        assertThat(response.getSettlements()).isEmpty();
        verify(groupAuthorizationService).requireGroupMember(requesterId, groupId);
    }

    @Test
    @DisplayName("Successful settlement calculation with user names enriched")
    void testSuccessfulSettlementEnriched() {
        Expense expense = new Expense(null, alice, "Dinner", 5000L, "equal");
        expense.addSplit(new ExpenseSplit(expense, alice, 2500L, null));
        expense.addSplit(new ExpenseSplit(expense, bob, 2500L, null));

        when(expenseRepository.findByGroupIdWithSplitsAndUsersOrderByCreatedAtDesc(groupId))
                .thenReturn(List.of(expense));
        when(settlementRepository.findByGroupId(groupId))
                .thenReturn(Collections.emptyList());

        Map<UUID, Long> rawBalances = new HashMap<>();
        rawBalances.put(aliceId, 2500L);
        rawBalances.put(bobId, -2500L);
        when(balanceCalculator.calculateFromExpenses(any())).thenReturn(rawBalances);

        when(debtSettlementCalculator.calculateSettlements(any()))
                .thenReturn(List.of(new CalculatedSettlement(bobId, aliceId, 2500L)));

        SuggestedSettlementsResponse response = settlementSuggestionService.getSuggestedSettlements(requesterId, groupId);

        assertThat(response.getSettlements()).hasSize(1);
        SuggestedSettlementDto dto = response.getSettlements().get(0);
        assertThat(dto.getFrom()).isEqualTo(bobId);
        assertThat(dto.getTo()).isEqualTo(aliceId);
        assertThat(dto.getAmount()).isEqualTo("2500");
        assertThat(dto.getFromName()).isEqualTo("Bob");
        assertThat(dto.getToName()).isEqualTo("Alice");
    }

    @Test
    @DisplayName("Recorded settlement adjusts balances correctly before debt calculation")
    void testRecordedSettlementAdjustment() {
        when(expenseRepository.findByGroupIdWithSplitsAndUsersOrderByCreatedAtDesc(groupId))
                .thenReturn(Collections.emptyList());

        Settlement recordedSettlement = new Settlement(null, bob, alice, 1000L);
        when(settlementRepository.findByGroupId(groupId))
                .thenReturn(List.of(recordedSettlement));

        Map<UUID, Long> initialBalances = new HashMap<>();
        initialBalances.put(aliceId, 1000L);
        initialBalances.put(bobId, -1000L);
        when(balanceCalculator.calculateFromExpenses(any())).thenReturn(initialBalances);

        when(debtSettlementCalculator.calculateSettlements(argThat(map ->
                map.get(aliceId) == 0L && map.get(bobId) == 0L
        ))).thenReturn(Collections.emptyList());

        SuggestedSettlementsResponse response = settlementSuggestionService.getSuggestedSettlements(requesterId, groupId);

        assertThat(response.getSettlements()).isEmpty();
    }
}
