package com.equisplit.service;

import com.equisplit.dto.response.GroupBalancesResponse;
import com.equisplit.dto.response.UserBalanceDto;
import com.equisplit.entity.Expense;
import com.equisplit.entity.ExpenseSplit;
import com.equisplit.entity.User;
import com.equisplit.repository.ExpenseRepository;
import com.equisplit.repository.UserRepository;
import com.equisplit.service.balance.GroupBalanceCalculator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class BalanceService {

    private final ExpenseRepository expenseRepository;
    private final UserRepository userRepository;
    private final GroupAuthorizationService groupAuthorizationService;
    private final GroupBalanceCalculator balanceCalculator;

    public BalanceService(ExpenseRepository expenseRepository,
                          UserRepository userRepository,
                          GroupAuthorizationService groupAuthorizationService,
                          GroupBalanceCalculator balanceCalculator) {
        this.expenseRepository = expenseRepository;
        this.userRepository = userRepository;
        this.groupAuthorizationService = groupAuthorizationService;
        this.balanceCalculator = balanceCalculator;
    }

    @Transactional(readOnly = true)
    public GroupBalancesResponse getGroupBalances(UUID requesterId, UUID groupId) {
        // 1. Check requester is a member of the group (throws 403 if not)
        groupAuthorizationService.requireGroupMember(requesterId, groupId);

        // 2. Fetch expenses with splits and users eagerly to avoid N+1 queries
        List<Expense> expenses = expenseRepository.findByGroupIdWithSplitsAndUsersOrderByCreatedAtDesc(groupId);

        // 3. Compute net balances
        Map<UUID, Long> balanceMap = balanceCalculator.calculateFromExpenses(expenses);

        // 4. Collect user details from already-fetched entities
        Map<UUID, User> userMap = new HashMap<>();
        for (Expense exp : expenses) {
            if (exp.getPaidBy() != null) {
                userMap.put(exp.getPaidBy().getId(), exp.getPaidBy());
            }
            if (exp.getSplits() != null) {
                for (ExpenseSplit split : exp.getSplits()) {
                    if (split.getUser() != null) {
                        userMap.put(split.getUser().getId(), split.getUser());
                    }
                }
            }
        }

        // Fetch any missing users if needed
        List<UUID> missingUserIds = balanceMap.keySet().stream()
                .filter(id -> !userMap.containsKey(id))
                .toList();
        if (!missingUserIds.isEmpty()) {
            userRepository.findAllById(missingUserIds)
                    .forEach(u -> userMap.put(u.getId(), u));
        }

        // 5. Build DTO response
        List<UserBalanceDto> balanceDtos = new ArrayList<>(balanceMap.size());
        for (Map.Entry<UUID, Long> entry : balanceMap.entrySet()) {
            UUID userId = entry.getKey();
            Long balance = entry.getValue();
            User user = userMap.get(userId);
            String name = user != null ? user.getName() : null;
            String email = user != null ? user.getEmail() : null;

            balanceDtos.add(new UserBalanceDto(userId, name, email, balance));
        }

        return new GroupBalancesResponse(balanceDtos);
    }
}
