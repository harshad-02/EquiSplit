package com.equisplit.service;

import com.equisplit.dto.request.CreateExpenseRequest;
import com.equisplit.dto.request.ExpenseSplitRequest;
import com.equisplit.dto.response.CreateExpenseResponse;
import com.equisplit.dto.response.ExpenseDetailDto;
import com.equisplit.dto.response.ExpenseDto;
import com.equisplit.dto.response.ExpenseSplitDto;
import com.equisplit.dto.response.GroupExpensesResponse;
import com.equisplit.entity.Expense;
import com.equisplit.entity.ExpenseSplit;
import com.equisplit.entity.Group;
import com.equisplit.entity.User;
import com.equisplit.exception.BadRequestException;
import com.equisplit.exception.NotFoundException;
import com.equisplit.repository.ExpenseRepository;
import com.equisplit.repository.ExpenseSplitRepository;
import com.equisplit.repository.GroupMemberRepository;
import com.equisplit.repository.GroupRepository;
import com.equisplit.repository.UserRepository;
import com.equisplit.service.split.CalculatedSplit;
import com.equisplit.service.split.ExpenseSplitCalculator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ExpenseService {

    private final ExpenseRepository expenseRepository;
    private final ExpenseSplitRepository expenseSplitRepository;
    private final GroupRepository groupRepository;
    private final UserRepository userRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final GroupAuthorizationService groupAuthorizationService;
    private final ExpenseSplitCalculator splitCalculator;

    public ExpenseService(ExpenseRepository expenseRepository,
                          ExpenseSplitRepository expenseSplitRepository,
                          GroupRepository groupRepository,
                          UserRepository userRepository,
                          GroupMemberRepository groupMemberRepository,
                          GroupAuthorizationService groupAuthorizationService,
                          ExpenseSplitCalculator splitCalculator) {
        this.expenseRepository = expenseRepository;
        this.expenseSplitRepository = expenseSplitRepository;
        this.groupRepository = groupRepository;
        this.userRepository = userRepository;
        this.groupMemberRepository = groupMemberRepository;
        this.groupAuthorizationService = groupAuthorizationService;
        this.splitCalculator = splitCalculator;
    }

    @Transactional
    public CreateExpenseResponse createExpense(UUID requesterId, UUID pathGroupId, CreateExpenseRequest request) {
        UUID groupId = pathGroupId != null ? pathGroupId : request.getGroupId();
        if (groupId == null) {
            throw new BadRequestException("groupId is required");
        }

        // 1. Check requester is a member of the group (throws 403 Forbidden if not)
        groupAuthorizationService.requireGroupMember(requesterId, groupId);

        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new NotFoundException("Group not found"));

        // 2. Validate payer (defaults to requester if not specified)
        UUID payerId = request.getPaidBy() != null ? request.getPaidBy() : requesterId;
        if (!groupMemberRepository.existsByIdGroupIdAndIdUserId(groupId, payerId)) {
            throw new BadRequestException("Payer must be a member of the group");
        }

        User payer = userRepository.findById(payerId)
                .orElseThrow(() -> new NotFoundException("Payer not found"));

        // 3. Validate split users
        List<ExpenseSplitRequest> splits = request.getSplits();
        if (splits == null || splits.isEmpty()) {
            throw new BadRequestException("At least one split is required");
        }

        Set<UUID> uniqueUserIds = new HashSet<>();
        for (ExpenseSplitRequest split : splits) {
            if (split.getUserId() == null) {
                throw new BadRequestException("Each split must contain a userId");
            }
            if (!uniqueUserIds.add(split.getUserId())) {
                throw new BadRequestException("A user cannot appear more than once in splits");
            }
        }

        // Check all split users are members of the group
        List<UUID> memberUserIds = groupMemberRepository.findExistingMemberUserIds(groupId, new ArrayList<>(uniqueUserIds));
        if (memberUserIds.size() != uniqueUserIds.size()) {
            throw new BadRequestException("All split users must be members of the group");
        }

        // Fetch user entities
        List<User> splitUsers = userRepository.findAllById(uniqueUserIds);
        if (splitUsers.size() != uniqueUserIds.size()) {
            throw new BadRequestException("Unknown user in splits");
        }
        Map<UUID, User> userMap = splitUsers.stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));

        // 4. Calculate splits
        List<CalculatedSplit> calculatedSplits = splitCalculator.calculate(
                request.getSplitType(),
                request.getTotalAmount(),
                splits
        );

        // 5. Persist Expense and Splits atomically
        Expense expense = new Expense(group, payer, request.getDescription(), request.getTotalAmount(), request.getSplitType());
        expense = expenseRepository.save(expense);

        List<ExpenseSplitDto> splitDtos = new ArrayList<>(calculatedSplits.size());
        for (CalculatedSplit cs : calculatedSplits) {
            User participant = userMap.get(cs.userId());
            ExpenseSplit splitEntity = new ExpenseSplit(expense, participant, cs.amount(), cs.percentage());
            expense.addSplit(splitEntity);
            expenseSplitRepository.save(splitEntity);

            splitDtos.add(new ExpenseSplitDto(cs.userId(), cs.amount(), cs.percentage(), participant.getName()));
        }

        ExpenseDto expenseDto = new ExpenseDto(
                expense.getId(),
                group.getId(),
                payer.getId(),
                expense.getDescription(),
                expense.getTotalAmount(),
                expense.getSplitType(),
                expense.getCreatedAt()
        );

        return new CreateExpenseResponse("Expense created successfully", expenseDto, splitDtos);
    }

    @Transactional(readOnly = true)
    public GroupExpensesResponse getGroupExpenses(UUID requesterId, UUID groupId) {
        groupAuthorizationService.requireGroupMember(requesterId, groupId);

        List<Expense> expenses = expenseRepository.findByGroupIdWithSplitsAndUsersOrderByCreatedAtDesc(groupId);
        List<ExpenseDetailDto> expenseDtos = new ArrayList<>(expenses.size());

        for (Expense exp : expenses) {
            List<ExpenseSplitDto> splitDtos = exp.getSplits().stream()
                    .map(s -> new ExpenseSplitDto(
                            s.getUser().getId(),
                            s.getAmount(),
                            s.getPercentage(),
                            s.getUser().getName()
                    ))
                    .toList();

            ExpenseDetailDto detailDto = new ExpenseDetailDto(
                    exp.getId(),
                    exp.getDescription(),
                    exp.getTotalAmount(),
                    exp.getSplitType(),
                    exp.getPaidBy().getId(),
                    exp.getPaidBy().getName(),
                    exp.getCreatedAt(),
                    splitDtos
            );
            expenseDtos.add(detailDto);
        }

        return new GroupExpensesResponse(expenseDtos);
    }
}
