package com.equisplit.service.split;

import java.math.BigDecimal;
import java.util.UUID;

public record CalculatedSplit(
        UUID userId,
        Long amount,
        BigDecimal percentage
) {
    public CalculatedSplit(UUID userId, Long amount) {
        this(userId, amount, null);
    }
}
