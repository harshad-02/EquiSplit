package com.equisplit.service.split;

import com.equisplit.dto.request.ExpenseSplitRequest;
import com.equisplit.exception.BadRequestException;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

@Component
public class ExpenseSplitCalculator {

    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final BigDecimal PERCENTAGE_TOLERANCE = new BigDecimal("0.0001");

    public List<CalculatedSplit> calculate(String splitType, Long totalAmount, List<ExpenseSplitRequest> splits) {
        if (totalAmount == null || totalAmount <= 0) {
            throw new BadRequestException("totalAmount must be a positive integer in paise");
        }

        if (splits == null || splits.isEmpty()) {
            throw new BadRequestException("At least one split is required");
        }

        if (splitType == null) {
            throw new BadRequestException("Invalid split type");
        }

        return switch (splitType.toLowerCase()) {
            case "equal" -> calculateEqual(totalAmount, splits);
            case "exact" -> calculateExact(totalAmount, splits);
            case "percentage" -> calculatePercentage(totalAmount, splits);
            default -> throw new BadRequestException("Invalid split type");
        };
    }

    private List<CalculatedSplit> calculateEqual(Long totalAmount, List<ExpenseSplitRequest> splits) {
        int numberOfUsers = splits.size();
        long baseAmount = totalAmount / numberOfUsers;
        long remainder = totalAmount % numberOfUsers;

        List<CalculatedSplit> result = new ArrayList<>(numberOfUsers);
        for (int i = 0; i < numberOfUsers; i++) {
            long amount = baseAmount + (i < remainder ? 1L : 0L);
            result.add(new CalculatedSplit(splits.get(i).getUserId(), amount, null));
        }
        return result;
    }

    private List<CalculatedSplit> calculateExact(Long totalAmount, List<ExpenseSplitRequest> splits) {
        long splitTotal = 0;
        List<CalculatedSplit> result = new ArrayList<>(splits.size());

        for (ExpenseSplitRequest split : splits) {
            if (split.getAmount() == null || split.getAmount() < 0) {
                throw new BadRequestException("Each split amount must be a non-negative integer in paise");
            }
            splitTotal += split.getAmount();
            result.add(new CalculatedSplit(split.getUserId(), split.getAmount(), null));
        }

        if (splitTotal != totalAmount) {
            throw new BadRequestException("Sum of split amounts must equal total amount");
        }

        return result;
    }

    private List<CalculatedSplit> calculatePercentage(Long totalAmount, List<ExpenseSplitRequest> splits) {
        BigDecimal percentageTotal = BigDecimal.ZERO;

        for (ExpenseSplitRequest split : splits) {
            BigDecimal pct = split.getPercentage();
            if (pct == null || pct.compareTo(BigDecimal.ZERO) < 0 || pct.compareTo(HUNDRED) > 0) {
                throw new BadRequestException("Each percentage must be a number between 0 and 100");
            }
            percentageTotal = percentageTotal.add(pct);
        }

        if (percentageTotal.subtract(HUNDRED).abs().compareTo(PERCENTAGE_TOLERANCE) > 0) {
            throw new BadRequestException("Sum of percentages must equal 100");
        }

        int numberOfUsers = splits.size();
        long[] amounts = new long[numberOfUsers];
        long calculatedTotal = 0;

        for (int i = 0; i < numberOfUsers; i++) {
            BigDecimal pct = splits.get(i).getPercentage();
            long rawAmount = BigDecimal.valueOf(totalAmount)
                    .multiply(pct)
                    .divide(HUNDRED, 0, RoundingMode.FLOOR)
                    .longValueExact();
            amounts[i] = rawAmount;
            calculatedTotal += rawAmount;
        }

        long remainder = totalAmount - calculatedTotal;
        for (int i = 0; i < numberOfUsers && remainder > 0; i++) {
            amounts[i] += 1;
            remainder--;
        }

        List<CalculatedSplit> result = new ArrayList<>(numberOfUsers);
        for (int i = 0; i < numberOfUsers; i++) {
            result.add(new CalculatedSplit(splits.get(i).getUserId(), amounts[i], splits.get(i).getPercentage()));
        }

        return result;
    }
}
