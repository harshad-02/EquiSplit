package com.equisplit.service;

import com.equisplit.dto.request.CreateExpenseRequest;
import com.equisplit.dto.request.ExpenseSplitRequest;
import com.equisplit.dto.response.CreateExpenseResponse;
import com.equisplit.dto.response.GroupExpensesResponse;
import com.equisplit.entity.Expense;
import com.equisplit.entity.ExpenseSplit;
import com.equisplit.entity.Group;
import com.equisplit.entity.GroupMember;
import com.equisplit.entity.User;
import com.equisplit.exception.BadRequestException;
import com.equisplit.exception.ForbiddenException;
import com.equisplit.repository.ExpenseRepository;
import com.equisplit.repository.ExpenseSplitRepository;
import com.equisplit.repository.GroupMemberRepository;
import com.equisplit.repository.GroupRepository;
import com.equisplit.repository.UserRepository;
import com.equisplit.service.split.CalculatedSplit;
import com.equisplit.service.split.ExpenseSplitCalculator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExpenseServiceTest {

    @Mock
    private ExpenseRepository expenseRepository;

    @Mock
    private ExpenseSplitRepository expenseSplitRepository;

    @Mock
    private GroupRepository groupRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private GroupMemberRepository groupMemberRepository;

    @Mock
    private GroupAuthorizationService groupAuthorizationService;

    @Mock
    private ExpenseSplitCalculator splitCalculator;

    @InjectMocks
    private ExpenseService expenseService;

    private UUID requesterId;
    private UUID payerId;
    private UUID participantId;
    private UUID groupId;
    private User requester;
    private User payer;
    private User participant;
    private Group group;
    private GroupMember mockMember;

    @BeforeEach
    void setUp() {
        requesterId = UUID.randomUUID();
        payerId = UUID.randomUUID();
        participantId = UUID.randomUUID();
        groupId = UUID.randomUUID();

        requester = new User("Alice", "alice@example.com", "hash", "user");
        requester.setId(requesterId);

        payer = new User("Bob", "bob@example.com", "hash", "user");
        payer.setId(payerId);

        participant = new User("Carol", "carol@example.com", "hash", "user");
        participant.setId(participantId);

        group = new Group("Goa Trip", requester);
        group.setId(groupId);

        mockMember = new GroupMember(group, requester, "admin");
    }

    @Test
    @DisplayName("Create expense successfully with equal split")
    void testCreateExpenseSuccess() {
        CreateExpenseRequest request = new CreateExpenseRequest(
                "Dinner", 10000L, "equal",
                List.of(new ExpenseSplitRequest(requesterId), new ExpenseSplitRequest(participantId))
        );

        when(groupAuthorizationService.requireGroupMember(requesterId, groupId)).thenReturn(mockMember);
        when(groupRepository.findById(groupId)).thenReturn(Optional.of(group));
        when(groupMemberRepository.existsByIdGroupIdAndIdUserId(groupId, requesterId)).thenReturn(true);
        when(userRepository.findById(requesterId)).thenReturn(Optional.of(requester));
        when(groupMemberRepository.findExistingMemberUserIds(eq(groupId), anyList()))
                .thenReturn(List.of(requesterId, participantId));
        when(userRepository.findAllById(any())).thenReturn(List.of(requester, participant));

        List<CalculatedSplit> calculatedSplits = List.of(
                new CalculatedSplit(requesterId, 5000L),
                new CalculatedSplit(participantId, 5000L)
        );
        when(splitCalculator.calculate(eq("equal"), eq(10000L), anyList())).thenReturn(calculatedSplits);

        Expense savedExpense = new Expense(group, requester, "Dinner", 10000L, "equal");
        savedExpense.setId(UUID.randomUUID());
        when(expenseRepository.save(any(Expense.class))).thenReturn(savedExpense);

        CreateExpenseResponse response = expenseService.createExpense(requesterId, groupId, request);

        assertThat(response.getMessage()).isEqualTo("Expense created successfully");
        assertThat(response.getExpense().getTotalAmount()).isEqualTo("10000");
        assertThat(response.getSplits()).hasSize(2);
        verify(expenseRepository).save(any(Expense.class));
        verify(expenseSplitRepository, org.mockito.Mockito.times(2)).save(any(ExpenseSplit.class));
    }

    @Test
    @DisplayName("Create expense with explicit different payer who is a member")
    void testCreateExpenseWithExplicitPayer() {
        CreateExpenseRequest request = new CreateExpenseRequest(
                "Dinner", 10000L, "equal",
                List.of(new ExpenseSplitRequest(requesterId), new ExpenseSplitRequest(payerId))
        );
        request.setPaidBy(payerId);

        when(groupAuthorizationService.requireGroupMember(requesterId, groupId)).thenReturn(mockMember);
        when(groupRepository.findById(groupId)).thenReturn(Optional.of(group));
        when(groupMemberRepository.existsByIdGroupIdAndIdUserId(groupId, payerId)).thenReturn(true);
        when(userRepository.findById(payerId)).thenReturn(Optional.of(payer));
        when(groupMemberRepository.findExistingMemberUserIds(eq(groupId), anyList()))
                .thenReturn(List.of(requesterId, payerId));
        when(userRepository.findAllById(any())).thenReturn(List.of(requester, payer));

        when(splitCalculator.calculate(eq("equal"), eq(10000L), anyList()))
                .thenReturn(List.of(new CalculatedSplit(requesterId, 5000L), new CalculatedSplit(payerId, 5000L)));

        Expense savedExpense = new Expense(group, payer, "Dinner", 10000L, "equal");
        savedExpense.setId(UUID.randomUUID());
        when(expenseRepository.save(any(Expense.class))).thenReturn(savedExpense);

        CreateExpenseResponse response = expenseService.createExpense(requesterId, groupId, request);

        assertThat(response.getExpense().getPaidBy()).isEqualTo(payerId);
    }

    @Test
    @DisplayName("Reject when requester is not a group member (403)")
    void testRequesterNotMember() {
        CreateExpenseRequest request = new CreateExpenseRequest(
                "Dinner", 10000L, "equal",
                List.of(new ExpenseSplitRequest(requesterId))
        );

        doThrow(new ForbiddenException("You are not a member of this group"))
                .when(groupAuthorizationService).requireGroupMember(requesterId, groupId);

        assertThatThrownBy(() -> expenseService.createExpense(requesterId, groupId, request))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("You are not a member of this group");
    }

    @Test
    @DisplayName("Reject when payer is not a group member")
    void testPayerNotMember() {
        CreateExpenseRequest request = new CreateExpenseRequest(
                "Dinner", 10000L, "equal",
                List.of(new ExpenseSplitRequest(requesterId))
        );
        request.setPaidBy(payerId);

        when(groupAuthorizationService.requireGroupMember(requesterId, groupId)).thenReturn(mockMember);
        when(groupRepository.findById(groupId)).thenReturn(Optional.of(group));
        when(groupMemberRepository.existsByIdGroupIdAndIdUserId(groupId, payerId)).thenReturn(false);

        assertThatThrownBy(() -> expenseService.createExpense(requesterId, groupId, request))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Payer must be a member of the group");
    }

    @Test
    @DisplayName("Reject when duplicate users in splits")
    void testDuplicateUserInSplits() {
        CreateExpenseRequest request = new CreateExpenseRequest(
                "Dinner", 10000L, "equal",
                List.of(new ExpenseSplitRequest(requesterId), new ExpenseSplitRequest(requesterId))
        );

        when(groupAuthorizationService.requireGroupMember(requesterId, groupId)).thenReturn(mockMember);
        when(groupRepository.findById(groupId)).thenReturn(Optional.of(group));
        when(groupMemberRepository.existsByIdGroupIdAndIdUserId(groupId, requesterId)).thenReturn(true);
        when(userRepository.findById(requesterId)).thenReturn(Optional.of(requester));

        assertThatThrownBy(() -> expenseService.createExpense(requesterId, groupId, request))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("A user cannot appear more than once in splits");
    }

    @Test
    @DisplayName("Reject when split participant is not a group member")
    void testSplitParticipantNotMember() {
        CreateExpenseRequest request = new CreateExpenseRequest(
                "Dinner", 10000L, "equal",
                List.of(new ExpenseSplitRequest(requesterId), new ExpenseSplitRequest(participantId))
        );

        when(groupAuthorizationService.requireGroupMember(requesterId, groupId)).thenReturn(mockMember);
        when(groupRepository.findById(groupId)).thenReturn(Optional.of(group));
        when(groupMemberRepository.existsByIdGroupIdAndIdUserId(groupId, requesterId)).thenReturn(true);
        when(userRepository.findById(requesterId)).thenReturn(Optional.of(requester));
        when(groupMemberRepository.findExistingMemberUserIds(eq(groupId), anyList()))
                .thenReturn(List.of(requesterId));

        assertThatThrownBy(() -> expenseService.createExpense(requesterId, groupId, request))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("All split users must be members of the group");
    }

    @Test
    @DisplayName("Reject when split participant user does not exist in users table")
    void testSplitParticipantUnknownUser() {
        CreateExpenseRequest request = new CreateExpenseRequest(
                "Dinner", 10000L, "equal",
                List.of(new ExpenseSplitRequest(requesterId), new ExpenseSplitRequest(participantId))
        );

        when(groupAuthorizationService.requireGroupMember(requesterId, groupId)).thenReturn(mockMember);
        when(groupRepository.findById(groupId)).thenReturn(Optional.of(group));
        when(groupMemberRepository.existsByIdGroupIdAndIdUserId(groupId, requesterId)).thenReturn(true);
        when(userRepository.findById(requesterId)).thenReturn(Optional.of(requester));
        when(groupMemberRepository.findExistingMemberUserIds(eq(groupId), anyList()))
                .thenReturn(List.of(requesterId, participantId));
        when(userRepository.findAllById(any())).thenReturn(List.of(requester));

        assertThatThrownBy(() -> expenseService.createExpense(requesterId, groupId, request))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Unknown user in splits");
    }

    @Test
    @DisplayName("Get group expenses successfully for member")
    void testGetGroupExpensesSuccess() {
        when(groupAuthorizationService.requireGroupMember(requesterId, groupId)).thenReturn(mockMember);

        Expense expense = new Expense(group, requester, "Dinner", 10000L, "equal");
        expense.setId(UUID.randomUUID());
        expense.setCreatedAt(Instant.now());
        ExpenseSplit split1 = new ExpenseSplit(expense, requester, 5000L, null);
        ExpenseSplit split2 = new ExpenseSplit(expense, participant, 5000L, null);
        expense.addSplit(split1);
        expense.addSplit(split2);

        when(expenseRepository.findByGroupIdWithSplitsAndUsersOrderByCreatedAtDesc(groupId))
                .thenReturn(List.of(expense));

        GroupExpensesResponse response = expenseService.getGroupExpenses(requesterId, groupId);

        assertThat(response.getExpenses()).hasSize(1);
        assertThat(response.getExpenses().get(0).getDescription()).isEqualTo("Dinner");
        assertThat(response.getExpenses().get(0).getPaidByName()).isEqualTo("Alice");
        assertThat(response.getExpenses().get(0).getSplits()).hasSize(2);
    }
}
