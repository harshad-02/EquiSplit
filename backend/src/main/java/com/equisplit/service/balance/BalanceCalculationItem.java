package com.equisplit.service.balance;

import java.util.List;
import java.util.UUID;

public record BalanceCalculationItem(
        UUID payerId,
        Long totalAmount,
        List<ParticipantSplit> splits
) {}
