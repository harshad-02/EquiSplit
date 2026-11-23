package com.equisplit.service.settlement;

import com.equisplit.dto.response.SuggestedSettlementDto;
import com.equisplit.dto.response.SuggestedSettlementsResponse;
import com.equisplit.entity.Expense;
import com.equisplit.entity.ExpenseSplit;
import com.equisplit.entity.Settlement;
import com.equisplit.entity.User;
import com.equisplit.repository.ExpenseRepository;
import com.equisplit.repository.SettlementRepository;
import com.equisplit.repository.UserRepository;
import com.equisplit.service.GroupAuthorizationService;
import com.equisplit.service.balance.GroupBalanceCalculator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class SettlementSuggestionService {

    private final GroupAuthorizationService groupAuthorizationService;
    private final ExpenseRepository expenseRepository;
    private final SettlementRepository settlementRepository;
    private final UserRepository userRepository;
    private final GroupBalanceCalculator balanceCalculator;
    private final DebtSettlementCalculator debtSettlementCalculator;

    public SettlementSuggestionService(
            GroupAuthorizationService groupAuthorizationService,
            ExpenseRepository expenseRepository,
            SettlementRepository settlementRepository,
            UserRepository userRepository,
            GroupBalanceCalculator balanceCalculator,
            DebtSettlementCalculator debtSettlementCalculator
    ) {
        this.groupAuthorizationService = groupAuthorizationService;
        this.expenseRepository = expenseRepository;
        this.settlementRepository = settlementRepository;
        this.userRepository = userRepository;
        this.balanceCalculator = balanceCalculator;
        this.debtSettlementCalculator = debtSettlementCalculator;
    }

    @Transactional(readOnly = true)
    public SuggestedSettlementsResponse getSuggestedSettlements(UUID requesterId, UUID groupId) {
        // 1. Check requester is a member of the group (throws ForbiddenException if not)
        groupAuthorizationService.requireGroupMember(requesterId, groupId);

        // 2. Fetch expenses with splits and users eagerly
        List<Expense> expenses = expenseRepository.findByGroupIdWithSplitsAndUsersOrderByCreatedAtDesc(groupId);

        // 3. Calculate initial net balances from expenses
        Map<UUID, Long> balanceMap = new LinkedHashMap<>(balanceCalculator.calculateFromExpenses(expenses));

        // 4. Fetch recorded settlements and adjust net balances
        List<Settlement> settlements = settlementRepository.findByGroupId(groupId);
        for (Settlement s : settlements) {
            UUID fromUser = s.getFromUser().getId();
            UUID toUser = s.getToUser().getId();
            long amount = s.getAmount();

            // Person who paid becomes less in debt (balance increases)
            balanceMap.put(fromUser, balanceMap.getOrDefault(fromUser, 0L) + amount);

            // Person who received has less money owed to them (balance decreases)
            balanceMap.put(toUser, balanceMap.getOrDefault(toUser, 0L) - amount);
        }

        // 5. Calculate suggested settlements using greedy max-heap algorithm
        List<CalculatedSettlement> calculatedList = debtSettlementCalculator.calculateSettlements(balanceMap);

        if (calculatedList.isEmpty()) {
            return new SuggestedSettlementsResponse(Collections.emptyList());
        }

        // 6. Gather user details to enrich with member names
        Map<UUID, String> userNames = new HashMap<>();
        for (Expense exp : expenses) {
            if (exp.getPaidBy() != null) {
                userNames.put(exp.getPaidBy().getId(), exp.getPaidBy().getName());
            }
            if (exp.getSplits() != null) {
                for (ExpenseSplit split : exp.getSplits()) {
                    if (split.getUser() != null) {
                        userNames.put(split.getUser().getId(), split.getUser().getName());
                    }
                }
            }
        }
        for (Settlement s : settlements) {
            if (s.getFromUser() != null) {
                userNames.put(s.getFromUser().getId(), s.getFromUser().getName());
            }
            if (s.getToUser() != null) {
                userNames.put(s.getToUser().getId(), s.getToUser().getName());
            }
        }

        // Identify any users not yet in userNames
        Set<UUID> neededUserIds = new HashSet<>();
        for (CalculatedSettlement cs : calculatedList) {
            if (!userNames.containsKey(cs.fromUser())) {
                neededUserIds.add(cs.fromUser());
            }
            if (!userNames.containsKey(cs.toUser())) {
                neededUserIds.add(cs.toUser());
            }
        }

        if (!neededUserIds.isEmpty()) {
            List<User> foundUsers = userRepository.findAllById(neededUserIds);
            for (User u : foundUsers) {
                userNames.put(u.getId(), u.getName());
            }
        }

        // 7. Map to DTOs
        List<SuggestedSettlementDto> dtoList = new ArrayList<>(calculatedList.size());
        for (CalculatedSettlement cs : calculatedList) {
            dtoList.add(SuggestedSettlementDto.builder()
                    .from(cs.fromUser())
                    .to(cs.toUser())
                    .amount(String.valueOf(cs.amount()))
                    .fromName(userNames.get(cs.fromUser()))
                    .toName(userNames.get(cs.toUser()))
                    .build());
        }

        return new SuggestedSettlementsResponse(dtoList);
    }
}
