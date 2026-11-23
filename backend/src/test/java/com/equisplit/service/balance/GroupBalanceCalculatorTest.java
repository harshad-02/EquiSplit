package com.equisplit.service.balance;

import com.equisplit.entity.Expense;
import com.equisplit.entity.ExpenseSplit;
import com.equisplit.entity.Group;
import com.equisplit.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class GroupBalanceCalculatorTest {

    private GroupBalanceCalculator calculator;
    private UUID aliceId;
    private UUID bobId;
    private UUID carolId;

    @BeforeEach
    void setUp() {
        calculator = new GroupBalanceCalculator();
        aliceId = UUID.randomUUID();
        bobId = UUID.randomUUID();
        carolId = UUID.randomUUID();
    }

    @Test
    @DisplayName("Single expense: Alice pays 10000, split equally between Alice and Bob")
    void testSingleExpenseEqualSplit() {
        BalanceCalculationItem item = new BalanceCalculationItem(
                aliceId,
                10000L,
                List.of(
                        new ParticipantSplit(aliceId, 5000L),
                        new ParticipantSplit(bobId, 5000L)
                )
        );

        Map<UUID, Long> balances = calculator.calculate(List.of(item));

        // Alice: +10000 - 5000 = +5000
        // Bob: 0 - 5000 = -5000
        assertThat(balances.get(aliceId)).isEqualTo(5000L);
        assertThat(balances.get(bobId)).isEqualTo(-5000L);
        assertZeroSumInvariant(balances);
    }

    @Test
    @DisplayName("One payer owes nothing: Carol pays 6000, split between Alice and Bob")
    void testPayerOwesNothing() {
        BalanceCalculationItem item = new BalanceCalculationItem(
                carolId,
                6000L,
                List.of(
                        new ParticipantSplit(aliceId, 3000L),
                        new ParticipantSplit(bobId, 3000L)
                )
        );

        Map<UUID, Long> balances = calculator.calculate(List.of(item));

        // Carol: +6000
        // Alice: -3000
        // Bob: -3000
        assertThat(balances.get(carolId)).isEqualTo(6000L);
        assertThat(balances.get(aliceId)).isEqualTo(-3000L);
        assertThat(balances.get(bobId)).isEqualTo(-3000L);
        assertZeroSumInvariant(balances);
    }

    @Test
    @DisplayName("Zero balance: Alice pays 5000, Alice's split is 5000")
    void testZeroBalanceUser() {
        BalanceCalculationItem item = new BalanceCalculationItem(
                aliceId,
                5000L,
                List.of(
                        new ParticipantSplit(aliceId, 5000L)
                )
        );

        Map<UUID, Long> balances = calculator.calculate(List.of(item));

        // Alice: +5000 - 5000 = 0
        assertThat(balances.get(aliceId)).isEqualTo(0L);
        assertZeroSumInvariant(balances);
    }

    @Test
    @DisplayName("Multiple expenses across multiple users (exact Node.js algorithm test scenario)")
    void testMultipleExpensesMultipleUsers() {
        // Expense 1: Alice pays 10000, split Alice 5000, Bob 5000
        BalanceCalculationItem exp1 = new BalanceCalculationItem(
                aliceId,
                10000L,
                List.of(
                        new ParticipantSplit(aliceId, 5000L),
                        new ParticipantSplit(bobId, 5000L)
                )
        );

        // Expense 2: Bob pays 6000, split Alice 3000, Bob 3000
        BalanceCalculationItem exp2 = new BalanceCalculationItem(
                bobId,
                6000L,
                List.of(
                        new ParticipantSplit(aliceId, 3000L),
                        new ParticipantSplit(bobId, 3000L)
                )
        );

        Map<UUID, Long> balances = calculator.calculate(List.of(exp1, exp2));

        // Alice: +10000 (paid exp1) - 5000 (owed exp1) - 3000 (owed exp2) = +2000
        // Bob: +6000 (paid exp2) - 5000 (owed exp1) - 3000 (owed exp2) = -2000
        assertThat(balances.get(aliceId)).isEqualTo(2000L);
        assertThat(balances.get(bobId)).isEqualTo(-2000L);
        assertZeroSumInvariant(balances);
    }

    @Test
    @DisplayName("Three participants, three expenses with unequal shares")
    void testThreeParticipantsThreeExpenses() {
        // Exp 1: Alice pays 15000, split Alice 5000, Bob 5000, Carol 5000
        BalanceCalculationItem exp1 = new BalanceCalculationItem(
                aliceId, 15000L,
                List.of(
                        new ParticipantSplit(aliceId, 5000L),
                        new ParticipantSplit(bobId, 5000L),
                        new ParticipantSplit(carolId, 5000L)
                )
        );

        // Exp 2: Bob pays 9000, split Alice 3000, Bob 3000, Carol 3000
        BalanceCalculationItem exp2 = new BalanceCalculationItem(
                bobId, 9000L,
                List.of(
                        new ParticipantSplit(aliceId, 3000L),
                        new ParticipantSplit(bobId, 3000L),
                        new ParticipantSplit(carolId, 3000L)
                )
        );

        // Exp 3: Carol pays 6000, split Alice 1000, Bob 2000, Carol 3000
        BalanceCalculationItem exp3 = new BalanceCalculationItem(
                carolId, 6000L,
                List.of(
                        new ParticipantSplit(aliceId, 1000L),
                        new ParticipantSplit(bobId, 2000L),
                        new ParticipantSplit(carolId, 3000L)
                )
        );

        Map<UUID, Long> balances = calculator.calculate(List.of(exp1, exp2, exp3));

        // Alice: +15000 - 5000 - 3000 - 1000 = +6000
        // Bob: +9000 - 5000 - 3000 - 2000 = -1000
        // Carol: +6000 - 5000 - 3000 - 3000 = -5000
        assertThat(balances.get(aliceId)).isEqualTo(6000L);
        assertThat(balances.get(bobId)).isEqualTo(-1000L);
        assertThat(balances.get(carolId)).isEqualTo(-5000L);
        assertZeroSumInvariant(balances);
    }

    @Test
    @DisplayName("Large amounts in paise do not suffer overflow or precision issues")
    void testLargeAmountsPrecision() {
        long largeTotal = 50_000_000_000L; // 500 million INR in paise
        long share = 25_000_000_000L;

        BalanceCalculationItem item = new BalanceCalculationItem(
                aliceId,
                largeTotal,
                List.of(
                        new ParticipantSplit(aliceId, share),
                        new ParticipantSplit(bobId, share)
                )
        );

        Map<UUID, Long> balances = calculator.calculate(List.of(item));

        assertThat(balances.get(aliceId)).isEqualTo(share);
        assertThat(balances.get(bobId)).isEqualTo(-share);
        assertZeroSumInvariant(balances);
    }

    @Test
    @DisplayName("Empty expenses list returns empty map")
    void testEmptyExpenses() {
        Map<UUID, Long> balances = calculator.calculate(List.of());
        assertThat(balances).isEmpty();

        Map<UUID, Long> balancesNull = calculator.calculate(null);
        assertThat(balancesNull).isEmpty();
    }

    @Test
    @DisplayName("calculateFromExpenses with JPA entity models produces identical result")
    void testCalculateFromExpensesEntities() {
        User userA = new User("Alice", "alice@example.com", "hash", "user");
        userA.setId(aliceId);
        User userB = new User("Bob", "bob@example.com", "hash", "user");
        userB.setId(bobId);
        Group group = new Group("Trip", userA);

        Expense exp = new Expense(group, userA, "Dinner", 10000L, "equal");
        exp.addSplit(new ExpenseSplit(exp, userA, 5000L, null));
        exp.addSplit(new ExpenseSplit(exp, userB, 5000L, null));

        Map<UUID, Long> balances = calculator.calculateFromExpenses(List.of(exp));

        assertThat(balances.get(aliceId)).isEqualTo(5000L);
        assertThat(balances.get(bobId)).isEqualTo(-5000L);
        assertZeroSumInvariant(balances);
    }

    private void assertZeroSumInvariant(Map<UUID, Long> balances) {
        long sum = 0L;
        for (Long balance : balances.values()) {
            sum += balance;
        }
        assertThat(sum).withFailMessage("Financial invariant violated: sum of all balances must be 0").isEqualTo(0L);
    }
}
