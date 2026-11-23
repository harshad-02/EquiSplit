package com.equisplit.service;

import com.equisplit.dto.response.GroupBalancesResponse;
import com.equisplit.entity.Expense;
import com.equisplit.entity.ExpenseSplit;
import com.equisplit.entity.Group;
import com.equisplit.entity.GroupMember;
import com.equisplit.entity.User;
import com.equisplit.exception.ForbiddenException;
import com.equisplit.repository.ExpenseRepository;
import com.equisplit.repository.UserRepository;
import com.equisplit.service.balance.GroupBalanceCalculator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BalanceServiceTest {

    @Mock
    private ExpenseRepository expenseRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private GroupAuthorizationService groupAuthorizationService;

    @Spy
    private GroupBalanceCalculator balanceCalculator = new GroupBalanceCalculator();

    @InjectMocks
    private BalanceService balanceService;

    private UUID requesterId;
    private UUID bobId;
    private UUID groupId;
    private User requester;
    private User bob;
    private Group group;
    private GroupMember mockMember;

    @BeforeEach
    void setUp() {
        requesterId = UUID.randomUUID();
        bobId = UUID.randomUUID();
        groupId = UUID.randomUUID();

        requester = new User("Alice", "alice@example.com", "hash", "user");
        requester.setId(requesterId);

        bob = new User("Bob", "bob@example.com", "hash", "user");
        bob.setId(bobId);

        group = new Group("Trip", requester);
        group.setId(groupId);

        mockMember = new GroupMember(group, requester, "admin");
    }

    @Test
    @DisplayName("Successfully calculate balances for group member")
    void testGetGroupBalancesSuccess() {
        when(groupAuthorizationService.requireGroupMember(requesterId, groupId)).thenReturn(mockMember);

        Expense exp = new Expense(group, requester, "Dinner", 10000L, "equal");
        exp.addSplit(new ExpenseSplit(exp, requester, 5000L, null));
        exp.addSplit(new ExpenseSplit(exp, bob, 5000L, null));

        when(expenseRepository.findByGroupIdWithSplitsAndUsersOrderByCreatedAtDesc(groupId))
                .thenReturn(List.of(exp));

        GroupBalancesResponse response = balanceService.getGroupBalances(requesterId, groupId);

        assertThat(response.getBalances()).hasSize(2);

        var aliceBal = response.getBalances().stream()
                .filter(b -> b.getUserId().equals(requesterId))
                .findFirst().orElseThrow();
        assertThat(aliceBal.getName()).isEqualTo("Alice");
        assertThat(aliceBal.getEmail()).isEqualTo("alice@example.com");
        assertThat(aliceBal.getBalance()).isEqualTo("5000");

        var bobBal = response.getBalances().stream()
                .filter(b -> b.getUserId().equals(bobId))
                .findFirst().orElseThrow();
        assertThat(bobBal.getName()).isEqualTo("Bob");
        assertThat(bobBal.getEmail()).isEqualTo("bob@example.com");
        assertThat(bobBal.getBalance()).isEqualTo("-5000");

        // Verify zero sum invariant
        long totalSum = response.getBalances().stream()
                .mapToLong(b -> Long.parseLong(b.getBalance()))
                .sum();
        assertThat(totalSum).isEqualTo(0L);
    }

    @Test
    @DisplayName("Reject when requester is not a group member (403 Forbidden)")
    void testRequesterNotMember() {
        doThrow(new ForbiddenException("You are not a member of this group"))
                .when(groupAuthorizationService).requireGroupMember(requesterId, groupId);

        assertThatThrownBy(() -> balanceService.getGroupBalances(requesterId, groupId))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("You are not a member of this group");
    }

    @Test
    @DisplayName("Empty group with no expenses returns empty balances list")
    void testEmptyExpenses() {
        when(groupAuthorizationService.requireGroupMember(requesterId, groupId)).thenReturn(mockMember);
        when(expenseRepository.findByGroupIdWithSplitsAndUsersOrderByCreatedAtDesc(groupId))
                .thenReturn(List.of());

        GroupBalancesResponse response = balanceService.getGroupBalances(requesterId, groupId);

        assertThat(response.getBalances()).isEmpty();
    }
}
