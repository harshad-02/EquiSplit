package com.equisplit.service.settlement;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DebtSettlementCalculatorTest {

    private DebtSettlementCalculator calculator;

    private UUID alice;
    private UUID bob;
    private UUID charlie;
    private UUID dave;

    @BeforeEach
    void setUp() {
        calculator = new DebtSettlementCalculator();
        alice = UUID.randomUUID();
        bob = UUID.randomUUID();
        charlie = UUID.randomUUID();
        dave = UUID.randomUUID();
    }

    @Test
    @DisplayName("Empty or null balances handling")
    void testEmptyAndNullBalances() {
        assertThat(calculator.calculateSettlements(Collections.emptyMap())).isEmpty();
        assertThatThrownBy(() -> calculator.calculateSettlements(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Balances map cannot be null");
    }

    @Test
    @DisplayName("All zero balances return empty settlement list")
    void testAllZeroBalances() {
        Map<UUID, Long> balances = new HashMap<>();
        balances.put(alice, 0L);
        balances.put(bob, 0L);
        balances.put(charlie, 0L);

        List<CalculatedSettlement> result = calculator.calculateSettlements(balances);
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Non-zero sum throws IllegalArgumentException")
    void testNonZeroSumThrowsException() {
        Map<UUID, Long> balances = new HashMap<>();
        balances.put(alice, 5000L);
        balances.put(bob, -4000L); // sum = +1000

        assertThatThrownBy(() -> calculator.calculateSettlements(balances))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Balances must sum to zero");
    }

    @Test
    @DisplayName("One creditor and one debtor (Alice +5000, Bob -5000)")
    void testOneCreditorOneDebtor() {
        Map<UUID, Long> balances = new HashMap<>();
        balances.put(alice, 5000L);
        balances.put(bob, -5000L);

        List<CalculatedSettlement> result = calculator.calculateSettlements(balances);

        assertThat(result).hasSize(1);
        CalculatedSettlement s = result.get(0);
        assertThat(s.fromUser()).isEqualTo(bob);
        assertThat(s.toUser()).isEqualTo(alice);
        assertThat(s.amount()).isEqualTo(5000L);

        verifySettlementInvariants(balances, result);
    }

    @Test
    @DisplayName("One creditor and multiple debtors (Alice +10000, Bob -6000, Charlie -4000)")
    void testOneCreditorMultipleDebtors() {
        Map<UUID, Long> balances = new HashMap<>();
        balances.put(alice, 10000L);
        balances.put(bob, -6000L);
        balances.put(charlie, -4000L);

        List<CalculatedSettlement> result = calculator.calculateSettlements(balances);

        assertThat(result).hasSize(2);
        long totalPaidToAlice = result.stream()
                .filter(s -> s.toUser().equals(alice))
                .mapToLong(CalculatedSettlement::amount)
                .sum();
        assertThat(totalPaidToAlice).isEqualTo(10000L);

        verifySettlementInvariants(balances, result);
    }

    @Test
    @DisplayName("Multiple creditors and one debtor (Alice +8000, Bob +4000, Charlie -12000)")
    void testMultipleCreditorsOneDebtor() {
        Map<UUID, Long> balances = new HashMap<>();
        balances.put(alice, 8000L);
        balances.put(bob, 4000L);
        balances.put(charlie, -12000L);

        List<CalculatedSettlement> result = calculator.calculateSettlements(balances);

        assertThat(result).hasSize(2);
        assertThat(result).allMatch(s -> s.fromUser().equals(charlie));
        verifySettlementInvariants(balances, result);
    }

    @Test
    @DisplayName("Multiple creditors and multiple debtors (Alice +8000, Bob +4000, Charlie -7000, Dave -5000)")
    void testMultipleCreditorsMultipleDebtors() {
        Map<UUID, Long> balances = new HashMap<>();
        balances.put(alice, 8000L);
        balances.put(bob, 4000L);
        balances.put(charlie, -7000L);
        balances.put(dave, -5000L);

        List<CalculatedSettlement> result = calculator.calculateSettlements(balances);

        assertThat(result).hasSize(3);
        verifySettlementInvariants(balances, result);
    }

    @Test
    @DisplayName("Zero balance users are ignored and do not affect settlements")
    void testZeroBalancesIgnored() {
        Map<UUID, Long> balances = new HashMap<>();
        balances.put(alice, 5000L);
        balances.put(bob, -5000L);
        balances.put(charlie, 0L);
        balances.put(dave, 0L);

        List<CalculatedSettlement> result = calculator.calculateSettlements(balances);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).fromUser()).isEqualTo(bob);
        assertThat(result.get(0).toUser()).isEqualTo(alice);
        assertThat(result.get(0).amount()).isEqualTo(5000L);
        verifySettlementInvariants(balances, result);
    }

    @Test
    @DisplayName("Large monetary values in paise without 32-bit overflow")
    void testLargeMonetaryValues() {
        Map<UUID, Long> balances = new HashMap<>();
        long bigAmount = 5_000_000_000_000L; // 50 billion rupees in paise
        balances.put(alice, bigAmount);
        balances.put(bob, -bigAmount);

        List<CalculatedSettlement> result = calculator.calculateSettlements(balances);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).amount()).isEqualTo(bigAmount);
        verifySettlementInvariants(balances, result);
    }

    @Test
    @DisplayName("Input map immutability - calculateSettlements does not modify input")
    void testInputMapImmutability() {
        Map<UUID, Long> balances = new HashMap<>();
        balances.put(alice, 1000L);
        balances.put(bob, -1000L);
        Map<UUID, Long> copy = new HashMap<>(balances);

        calculator.calculateSettlements(balances);

        assertThat(balances).isEqualTo(copy);
    }

    @Test
    @DisplayName("CalculatedSettlement record validation")
    void testCalculatedSettlementValidation() {
        assertThatThrownBy(() -> new CalculatedSettlement(null, bob, 100L))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new CalculatedSettlement(alice, null, 100L))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new CalculatedSettlement(alice, bob, null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new CalculatedSettlement(alice, alice, 100L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("same user");
        assertThatThrownBy(() -> new CalculatedSettlement(alice, bob, 0L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("strictly positive");
        assertThatThrownBy(() -> new CalculatedSettlement(alice, bob, -50L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("strictly positive");
    }

    private void verifySettlementInvariants(Map<UUID, Long> initialBalances, List<CalculatedSettlement> settlements) {
        Map<UUID, Long> runningBalances = new HashMap<>(initialBalances);

        for (CalculatedSettlement s : settlements) {
            assertThat(s.amount()).isPositive();
            assertThat(s.fromUser()).isNotEqualTo(s.toUser());

            runningBalances.put(s.fromUser(), runningBalances.get(s.fromUser()) + s.amount());
            runningBalances.put(s.toUser(), runningBalances.get(s.toUser()) - s.amount());
        }

        for (Map.Entry<UUID, Long> entry : runningBalances.entrySet()) {
            assertThat(entry.getValue())
                    .as("Balance for user %s after settlements must be 0", entry.getKey())
                    .isEqualTo(0L);
        }
    }
}
