package com.equisplit.service.balance;

import java.util.UUID;

public record ParticipantSplit(
        UUID userId,
        Long amount
) {}
