package com.equisplit.service;

import com.equisplit.dto.request.ExpenseSplitRequest;
import com.equisplit.service.balance.BalanceCalculationItem;
import com.equisplit.service.balance.GroupBalanceCalculator;
import com.equisplit.service.balance.ParticipantSplit;
import com.equisplit.service.settlement.CalculatedSettlement;
import com.equisplit.service.settlement.DebtSettlementCalculator;
import com.equisplit.service.split.CalculatedSplit;
import com.equisplit.service.split.ExpenseSplitCalculator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

class FinancialInvariantsTest {

    private ExpenseSplitCalculator splitCalculator;
    private GroupBalanceCalculator balanceCalculator;
    private DebtSettlementCalculator settlementCalculator;

    @BeforeEach
    void setUp() {
        splitCalculator = new ExpenseSplitCalculator();
        balanceCalculator = new GroupBalanceCalculator();
        settlementCalculator = new DebtSettlementCalculator();
    }

    @Test
    @DisplayName("Invariant: Equal split sum always strictly equals total amount (including non-divisible odd paise)")
    void testEqualSplitSumInvariant() {
        long[] testAmounts = {1L, 2L, 3L, 7L, 100L, 101L, 999999L, 100000000L};
        int[] userCounts = {2, 3, 4, 5, 7, 10};

        for (long total : testAmounts) {
            for (int count : userCounts) {
                List<ExpenseSplitRequest> requests = new ArrayList<>();
                for (int i = 0; i < count; i++) {
                    requests.add(new ExpenseSplitRequest(UUID.randomUUID()));
                }

                List<CalculatedSplit> result = splitCalculator.calculate("equal", total, requests);
                long sum = result.stream().mapToLong(CalculatedSplit::amount).sum();
                assertThat(sum)
                        .as("Sum of equal splits for total %d across %d users must equal total", total, count)
                        .isEqualTo(total);
            }
        }
    }

    @Test
    @DisplayName("Invariant: Group net balances across all members strictly sum to zero (Zero-Sum Invariant)")
    void testZeroSumBalanceInvariantAcrossMultipleExpenses() {
        UUID u1 = UUID.randomUUID();
        UUID u2 = UUID.randomUUID();
        UUID u3 = UUID.randomUUID();
        UUID u4 = UUID.randomUUID();

        // Expense 1: u1 pays 10000, split across u1, u2, u3, u4
        BalanceCalculationItem exp1 = new BalanceCalculationItem(
                u1, 10000L,
                List.of(
                        new ParticipantSplit(u1, 2500L),
                        new ParticipantSplit(u2, 2500L),
                        new ParticipantSplit(u3, 2500L),
                        new ParticipantSplit(u4, 2500L)
                )
        );

        // Expense 2: u2 pays 5003, split across u2, u3, u4 (odd remainder)
        BalanceCalculationItem exp2 = new BalanceCalculationItem(
                u2, 5003L,
                List.of(
                        new ParticipantSplit(u2, 1668L),
                        new ParticipantSplit(u3, 1668L),
                        new ParticipantSplit(u4, 1667L)
                )
        );

        // Expense 3: u3 pays 3000, split across u1 and u4
        BalanceCalculationItem exp3 = new BalanceCalculationItem(
                u3, 3000L,
                List.of(
                        new ParticipantSplit(u1, 1500L),
                        new ParticipantSplit(u4, 1500L)
                )
        );

        Map<UUID, Long> balances = balanceCalculator.calculate(List.of(exp1, exp2, exp3));

        long sumOfBalances = balances.values().stream().mapToLong(Long::longValue).sum();
        assertThat(sumOfBalances).as("Net balances sum must be exactly zero").isZero();
    }

    @Test
    @DisplayName("Invariant: Settlement algorithm fully consolidates debt and balances sum to zero post-settlement")
    void testDebtSettlementConsolidationInvariant() {
        UUID alice = UUID.randomUUID();
        UUID bob = UUID.randomUUID();
        UUID charlie = UUID.randomUUID();
        UUID dave = UUID.randomUUID();

        Map<UUID, Long> balances = new HashMap<>();
        balances.put(alice, 15000L);   // Creditor
        balances.put(bob, 5000L);      // Creditor
        balances.put(charlie, -12000L); // Debtor
        balances.put(dave, -8000L);     // Debtor

        List<CalculatedSettlement> settlements = settlementCalculator.calculateSettlements(balances);

        // Track net changes from suggested settlements
        Map<UUID, Long> netTransfers = new HashMap<>();
        for (UUID u : balances.keySet()) {
            netTransfers.put(u, 0L);
        }

        long totalTransferred = 0L;
        for (CalculatedSettlement cs : settlements) {
            assertThat(cs.amount()).isPositive();
            assertThat(cs.fromUser()).isNotEqualTo(cs.toUser());
            totalTransferred += cs.amount();

            netTransfers.put(cs.fromUser(), netTransfers.get(cs.fromUser()) + cs.amount()); // pays debt
            netTransfers.put(cs.toUser(), netTransfers.get(cs.toUser()) - cs.amount());     // receives credit
        }

        // Total transferred must equal total debt (12000 + 8000 = 20000)
        assertThat(totalTransferred).isEqualTo(20000L);

        // Verify each user's balance is resolved to 0
        for (Map.Entry<UUID, Long> entry : balances.entrySet()) {
            long finalBalance = entry.getValue() + netTransfers.get(entry.getKey());
            assertThat(finalBalance).as("User %s balance must be resolved to 0", entry.getKey()).isZero();
        }
    }

    @Test
    @DisplayName("Determinism: Calculating settlements twice for identical input produces identical transactions")
    void testSettlementDeterminism() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID c = UUID.randomUUID();

        Map<UUID, Long> balances = Map.of(
                a, 7000L,
                b, -4000L,
                c, -3000L
        );

        List<CalculatedSettlement> run1 = settlementCalculator.calculateSettlements(balances);
        List<CalculatedSettlement> run2 = settlementCalculator.calculateSettlements(balances);

        assertThat(run1).hasSize(run2.size());
        for (int i = 0; i < run1.size(); i++) {
            assertThat(run1.get(i).fromUser()).isEqualTo(run2.get(i).fromUser());
            assertThat(run1.get(i).toUser()).isEqualTo(run2.get(i).toUser());
            assertThat(run1.get(i).amount()).isEqualTo(run2.get(i).amount());
        }
    }

    @Test
    @DisplayName("Large Value / No Floating Point: Handles large amounts up to 10 billion paise without overflow")
    void testLargePaiseAmounts() {
        UUID user1 = UUID.randomUUID();
        UUID user2 = UUID.randomUUID();

        long largeAmount = 10_000_000_000L; // 100 million INR in paise
        List<ExpenseSplitRequest> splits = List.of(
                new ExpenseSplitRequest(user1),
                new ExpenseSplitRequest(user2)
        );

        List<CalculatedSplit> result = splitCalculator.calculate("equal", largeAmount, splits);
        assertThat(result.get(0).amount()).isEqualTo(5_000_000_000L);
        assertThat(result.get(1).amount()).isEqualTo(5_000_000_000L);
        assertThat(result.get(0).amount() + result.get(1).amount()).isEqualTo(largeAmount);
    }
}
