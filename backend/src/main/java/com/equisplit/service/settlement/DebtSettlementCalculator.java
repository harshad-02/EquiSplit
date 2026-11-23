package com.equisplit.service.settlement;

import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Pure domain component implementing the greedy MaxHeap-based debt settlement algorithm.
 *
 * It deterministically consolidates outstanding balances into bilateral transactions
 * in O(N log N) time after balance construction, eliminating circular and intermediate debts.
 */
@Component
public class DebtSettlementCalculator {

    /**
     * Internal entry representing a user and their balance magnitude.
     */
    private record BalanceEntry(UUID userId, long amount) {}

    /**
     * Calculates a consolidated set of bilateral transactions to settle debts given the net balance map.
     *
     * @param balances Map of userId to net balance in paise (positive = creditor, negative = debtor)
     * @return List of calculated settlements
     * @throws IllegalArgumentException if balances map is null or does not sum to zero
     */
    public List<CalculatedSettlement> calculateSettlements(Map<UUID, Long> balances) {
        if (balances == null) {
            throw new IllegalArgumentException("Balances map cannot be null");
        }

        if (balances.isEmpty()) {
            return Collections.emptyList();
        }

        // 1. Verify zero-sum invariant across all balances
        long totalSum = 0L;
        for (Map.Entry<UUID, Long> entry : balances.entrySet()) {
            Long val = entry.getValue();
            if (val != null) {
                totalSum = Math.addExact(totalSum, val);
            }
        }

        if (totalSum != 0L) {
            throw new IllegalArgumentException(
                    "Balances must sum to zero before settlement calculation. Current sum: " + totalSum
            );
        }

        // 2. Max-heap comparators: order by descending amount, tie-break deterministically by userId
        Comparator<BalanceEntry> comparator = (a, b) -> {
            int cmp = Long.compare(b.amount(), a.amount());
            if (cmp != 0) {
                return cmp;
            }
            return a.userId().compareTo(b.userId());
        };

        PriorityQueue<BalanceEntry> creditors = new PriorityQueue<>(comparator);
        PriorityQueue<BalanceEntry> debtors = new PriorityQueue<>(comparator);

        // 3. Populate priority queues, discarding zero balances
        for (Map.Entry<UUID, Long> entry : balances.entrySet()) {
            UUID userId = entry.getKey();
            Long balance = entry.getValue();

            if (userId == null || balance == null || balance == 0L) {
                continue;
            }

            if (balance > 0L) {
                creditors.offer(new BalanceEntry(userId, balance));
            } else {
                debtors.offer(new BalanceEntry(userId, -balance));
            }
        }

        List<CalculatedSettlement> settlements = new ArrayList<>();

        // 4. Greedy matching loop
        while (!debtors.isEmpty() && !creditors.isEmpty()) {
            BalanceEntry debtor = debtors.poll();
            BalanceEntry creditor = creditors.poll();

            long payment = Math.min(debtor.amount(), creditor.amount());

            settlements.add(new CalculatedSettlement(debtor.userId(), creditor.userId(), payment));

            long remainingDebt = debtor.amount() - payment;
            long remainingCredit = creditor.amount() - payment;

            if (remainingDebt > 0L) {
                debtors.offer(new BalanceEntry(debtor.userId(), remainingDebt));
            }

            if (remainingCredit > 0L) {
                creditors.offer(new BalanceEntry(creditor.userId(), remainingCredit));
            }
        }

        return settlements;
    }
}
