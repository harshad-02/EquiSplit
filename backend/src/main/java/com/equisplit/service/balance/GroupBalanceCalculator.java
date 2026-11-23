package com.equisplit.service.balance;

import com.equisplit.entity.Expense;
import com.equisplit.entity.ExpenseSplit;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class GroupBalanceCalculator {

    public Map<UUID, Long> calculate(List<BalanceCalculationItem> items) {
        Map<UUID, Long> balances = new LinkedHashMap<>();

        if (items == null || items.isEmpty()) {
            return balances;
        }

        for (BalanceCalculationItem item : items) {
            UUID payerId = item.payerId();
            long paidAmount = item.totalAmount();

            balances.put(payerId, balances.getOrDefault(payerId, 0L) + paidAmount);

            if (item.splits() != null) {
                for (ParticipantSplit split : item.splits()) {
                    UUID splitUserId = split.userId();
                    long owedAmount = split.amount();

                    balances.put(splitUserId, balances.getOrDefault(splitUserId, 0L) - owedAmount);
                }
            }
        }

        return balances;
    }

    public Map<UUID, Long> calculateFromExpenses(List<Expense> expenses) {
        Map<UUID, Long> balances = new LinkedHashMap<>();

        if (expenses == null || expenses.isEmpty()) {
            return balances;
        }

        for (Expense expense : expenses) {
            UUID payerId = expense.getPaidBy().getId();
            long paidAmount = expense.getTotalAmount();

            balances.put(payerId, balances.getOrDefault(payerId, 0L) + paidAmount);

            if (expense.getSplits() != null) {
                for (ExpenseSplit split : expense.getSplits()) {
                    UUID splitUserId = split.getUser().getId();
                    long owedAmount = split.getAmount();

                    balances.put(splitUserId, balances.getOrDefault(splitUserId, 0L) - owedAmount);
                }
            }
        }

        return balances;
    }
}
