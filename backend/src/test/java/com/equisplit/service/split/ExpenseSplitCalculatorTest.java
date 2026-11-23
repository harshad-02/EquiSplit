package com.equisplit.service.split;

import com.equisplit.dto.request.ExpenseSplitRequest;
import com.equisplit.exception.BadRequestException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExpenseSplitCalculatorTest {

    private ExpenseSplitCalculator calculator;
    private UUID user1;
    private UUID user2;
    private UUID user3;

    @BeforeEach
    void setUp() {
        calculator = new ExpenseSplitCalculator();
        user1 = UUID.randomUUID();
        user2 = UUID.randomUUID();
        user3 = UUID.randomUUID();
    }

    @Nested
    @DisplayName("Equal Split Calculation")
    class EqualSplitTests {

        @Test
        @DisplayName("Even division: 100 paise between 2 users -> 50, 50")
        void testEvenDivision() {
            List<ExpenseSplitRequest> splits = List.of(
                    new ExpenseSplitRequest(user1),
                    new ExpenseSplitRequest(user2)
            );

            List<CalculatedSplit> result = calculator.calculate("equal", 100L, splits);

            assertThat(result).hasSize(2);
            assertThat(result.get(0).amount()).isEqualTo(50L);
            assertThat(result.get(1).amount()).isEqualTo(50L);
            assertInvariants(100L, result);
        }

        @Test
        @DisplayName("Remainder = 1: 100 paise between 3 users -> 34, 33, 33")
        void testRemainderOne() {
            List<ExpenseSplitRequest> splits = List.of(
                    new ExpenseSplitRequest(user1),
                    new ExpenseSplitRequest(user2),
                    new ExpenseSplitRequest(user3)
            );

            List<CalculatedSplit> result = calculator.calculate("equal", 100L, splits);

            assertThat(result).hasSize(3);
            assertThat(result.get(0).amount()).isEqualTo(34L);
            assertThat(result.get(1).amount()).isEqualTo(33L);
            assertThat(result.get(2).amount()).isEqualTo(33L);
            assertInvariants(100L, result);
        }

        @Test
        @DisplayName("Remainder = 2: 101 paise between 3 users -> 34, 34, 33")
        void testRemainderTwo() {
            List<ExpenseSplitRequest> splits = List.of(
                    new ExpenseSplitRequest(user1),
                    new ExpenseSplitRequest(user2),
                    new ExpenseSplitRequest(user3)
            );

            List<CalculatedSplit> result = calculator.calculate("equal", 101L, splits);

            assertThat(result).hasSize(3);
            assertThat(result.get(0).amount()).isEqualTo(34L);
            assertThat(result.get(1).amount()).isEqualTo(34L);
            assertThat(result.get(2).amount()).isEqualTo(33L);
            assertInvariants(101L, result);
        }

        @Test
        @DisplayName("More participants than amount: 2 paise between 3 users -> 1, 1, 0")
        void testMoreParticipantsThanAmount() {
            List<ExpenseSplitRequest> splits = List.of(
                    new ExpenseSplitRequest(user1),
                    new ExpenseSplitRequest(user2),
                    new ExpenseSplitRequest(user3)
            );

            List<CalculatedSplit> result = calculator.calculate("equal", 2L, splits);

            assertThat(result).hasSize(3);
            assertThat(result.get(0).amount()).isEqualTo(1L);
            assertThat(result.get(1).amount()).isEqualTo(1L);
            assertThat(result.get(2).amount()).isEqualTo(0L);
            assertInvariants(2L, result);
        }

        @Test
        @DisplayName("Large amount preserves sum without integer overflow")
        void testLargeAmount() {
            long largeAmount = 10_000_000_000L; // 100 million rupees in paise
            List<ExpenseSplitRequest> splits = List.of(
                    new ExpenseSplitRequest(user1),
                    new ExpenseSplitRequest(user2),
                    new ExpenseSplitRequest(user3)
            );

            List<CalculatedSplit> result = calculator.calculate("equal", largeAmount, splits);

            assertInvariants(largeAmount, result);
        }
    }

    @Nested
    @DisplayName("Exact Split Calculation")
    class ExactSplitTests {

        @Test
        @DisplayName("Valid exact split: sum matches totalAmount")
        void testValidExactSplit() {
            List<ExpenseSplitRequest> splits = List.of(
                    new ExpenseSplitRequest(user1, 6000L),
                    new ExpenseSplitRequest(user2, 4000L)
            );

            List<CalculatedSplit> result = calculator.calculate("exact", 10000L, splits);

            assertThat(result).hasSize(2);
            assertThat(result.get(0).amount()).isEqualTo(6000L);
            assertThat(result.get(1).amount()).isEqualTo(4000L);
            assertInvariants(10000L, result);
        }

        @Test
        @DisplayName("Valid exact split with 0 amount for one participant")
        void testExactSplitWithZero() {
            List<ExpenseSplitRequest> splits = List.of(
                    new ExpenseSplitRequest(user1, 1000L),
                    new ExpenseSplitRequest(user2, 0L)
            );

            List<CalculatedSplit> result = calculator.calculate("exact", 1000L, splits);

            assertThat(result.get(0).amount()).isEqualTo(1000L);
            assertThat(result.get(1).amount()).isEqualTo(0L);
            assertInvariants(1000L, result);
        }

        @Test
        @DisplayName("Reject when sum is less than total amount")
        void testSumLessThanTotal() {
            List<ExpenseSplitRequest> splits = List.of(
                    new ExpenseSplitRequest(user1, 5000L),
                    new ExpenseSplitRequest(user2, 4000L) // 9000 != 10000
            );

            assertThatThrownBy(() -> calculator.calculate("exact", 10000L, splits))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessage("Sum of split amounts must equal total amount");
        }

        @Test
        @DisplayName("Reject when sum is greater than total amount")
        void testSumGreaterThanTotal() {
            List<ExpenseSplitRequest> splits = List.of(
                    new ExpenseSplitRequest(user1, 6000L),
                    new ExpenseSplitRequest(user2, 5000L) // 11000 != 10000
            );

            assertThatThrownBy(() -> calculator.calculate("exact", 10000L, splits))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessage("Sum of split amounts must equal total amount");
        }

        @Test
        @DisplayName("Reject negative exact split amount")
        void testNegativeExactAmount() {
            List<ExpenseSplitRequest> splits = List.of(
                    new ExpenseSplitRequest(user1, -100L),
                    new ExpenseSplitRequest(user2, 1100L)
            );

            assertThatThrownBy(() -> calculator.calculate("exact", 1000L, splits))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessage("Each split amount must be a non-negative integer in paise");
        }

        @Test
        @DisplayName("Reject null exact split amount")
        void testNullExactAmount() {
            ExpenseSplitRequest nullAmountSplit = new ExpenseSplitRequest(user1);
            nullAmountSplit.setAmount(null);
            List<ExpenseSplitRequest> splits = List.of(
                    nullAmountSplit,
                    new ExpenseSplitRequest(user2, 1000L)
            );

            assertThatThrownBy(() -> calculator.calculate("exact", 1000L, splits))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessage("Each split amount must be a non-negative integer in paise");
        }
    }

    @Nested
    @DisplayName("Percentage Split Calculation")
    class PercentageSplitTests {

        @Test
        @DisplayName("Valid percentage split: 70% and 30% of 20000 paise")
        void testValidPercentageSplit() {
            List<ExpenseSplitRequest> splits = List.of(
                    new ExpenseSplitRequest(user1, new BigDecimal("70")),
                    new ExpenseSplitRequest(user2, new BigDecimal("30"))
            );

            List<CalculatedSplit> result = calculator.calculate("percentage", 20000L, splits);

            assertThat(result).hasSize(2);
            assertThat(result.get(0).amount()).isEqualTo(14000L);
            assertThat(result.get(1).amount()).isEqualTo(6000L);
            assertInvariants(20000L, result);
        }

        @Test
        @DisplayName("Rounding & remainder handling: 33.33%, 33.33%, 33.34% of 100 paise -> 34, 33, 33")
        void testDecimalPercentageRemainderDistribution() {
            List<ExpenseSplitRequest> splits = List.of(
                    new ExpenseSplitRequest(user1, new BigDecimal("33.33")),
                    new ExpenseSplitRequest(user2, new BigDecimal("33.33")),
                    new ExpenseSplitRequest(user3, new BigDecimal("33.34"))
            );

            List<CalculatedSplit> result = calculator.calculate("percentage", 100L, splits);

            assertThat(result).hasSize(3);
            assertThat(result.get(0).amount()).isEqualTo(34L);
            assertThat(result.get(1).amount()).isEqualTo(33L);
            assertThat(result.get(2).amount()).isEqualTo(33L);
            assertInvariants(100L, result);
        }

        @Test
        @DisplayName("Reject when percentage sum is less than 100%")
        void testPercentageSumLessThanHundred() {
            List<ExpenseSplitRequest> splits = List.of(
                    new ExpenseSplitRequest(user1, new BigDecimal("50")),
                    new ExpenseSplitRequest(user2, new BigDecimal("40")) // 90%
            );

            assertThatThrownBy(() -> calculator.calculate("percentage", 10000L, splits))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessage("Sum of percentages must equal 100");
        }

        @Test
        @DisplayName("Reject when percentage sum is greater than 100%")
        void testPercentageSumGreaterThanHundred() {
            List<ExpenseSplitRequest> splits = List.of(
                    new ExpenseSplitRequest(user1, new BigDecimal("60")),
                    new ExpenseSplitRequest(user2, new BigDecimal("50")) // 110%
            );

            assertThatThrownBy(() -> calculator.calculate("percentage", 10000L, splits))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessage("Sum of percentages must equal 100");
        }

        @Test
        @DisplayName("Reject negative percentage")
        void testNegativePercentage() {
            List<ExpenseSplitRequest> splits = List.of(
                    new ExpenseSplitRequest(user1, new BigDecimal("-10")),
                    new ExpenseSplitRequest(user2, new BigDecimal("110"))
            );

            assertThatThrownBy(() -> calculator.calculate("percentage", 10000L, splits))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessage("Each percentage must be a number between 0 and 100");
        }

        @Test
        @DisplayName("Reject percentage greater than 100")
        void testPercentageGreaterThanHundred() {
            List<ExpenseSplitRequest> splits = List.of(
                    new ExpenseSplitRequest(user1, new BigDecimal("105")),
                    new ExpenseSplitRequest(user2, new BigDecimal("-5"))
            );

            assertThatThrownBy(() -> calculator.calculate("percentage", 10000L, splits))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessage("Each percentage must be a number between 0 and 100");
        }

        @Test
        @DisplayName("Reject null percentage")
        void testNullPercentage() {
            ExpenseSplitRequest nullPctSplit = new ExpenseSplitRequest(user1);
            nullPctSplit.setPercentage(null);
            List<ExpenseSplitRequest> splits = List.of(
                    nullPctSplit,
                    new ExpenseSplitRequest(user2, new BigDecimal("100"))
            );

            assertThatThrownBy(() -> calculator.calculate("percentage", 10000L, splits))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessage("Each percentage must be a number between 0 and 100");
        }
    }

    @Nested
    @DisplayName("General Input Validations")
    class GeneralValidationTests {

        @ParameterizedTest
        @ValueSource(longs = {0L, -1L, -5000L})
        @DisplayName("Reject non-positive total amount")
        void testNonPositiveTotalAmount(long amount) {
            List<ExpenseSplitRequest> splits = List.of(new ExpenseSplitRequest(user1));

            assertThatThrownBy(() -> calculator.calculate("equal", amount, splits))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessage("totalAmount must be a positive integer in paise");
        }

        @Test
        @DisplayName("Reject null total amount")
        void testNullTotalAmount() {
            List<ExpenseSplitRequest> splits = List.of(new ExpenseSplitRequest(user1));

            assertThatThrownBy(() -> calculator.calculate("equal", null, splits))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessage("totalAmount must be a positive integer in paise");
        }

        @Test
        @DisplayName("Reject empty splits list")
        void testEmptySplits() {
            assertThatThrownBy(() -> calculator.calculate("equal", 1000L, List.of()))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessage("At least one split is required");
        }

        @Test
        @DisplayName("Reject invalid split type")
        void testInvalidSplitType() {
            List<ExpenseSplitRequest> splits = List.of(new ExpenseSplitRequest(user1));

            assertThatThrownBy(() -> calculator.calculate("custom_type", 1000L, splits))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessage("Invalid split type");
        }
    }

    private void assertInvariants(long expectedTotal, List<CalculatedSplit> splits) {
        long actualSum = 0;
        for (CalculatedSplit split : splits) {
            assertThat(split.amount()).isGreaterThanOrEqualTo(0L);
            actualSum += split.amount();
        }
        assertThat(actualSum).isEqualTo(expectedTotal);
    }
}
