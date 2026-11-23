package com.equisplit.service.settlement;

import java.util.Objects;
import java.util.UUID;

/**
 * Represents a single directed settlement payment from a debtor to a creditor.
 *
 * @param fromUser The user who should make the payment (debtor)
 * @param toUser   The user who should receive the payment (creditor)
 * @param amount   The amount in paise (must be strictly positive)
 */
public record CalculatedSettlement(
        UUID fromUser,
        UUID toUser,
        Long amount
) {
    public CalculatedSettlement {
        Objects.requireNonNull(fromUser, "fromUser must not be null");
        Objects.requireNonNull(toUser, "toUser must not be null");
        Objects.requireNonNull(amount, "amount must not be null");

        if (fromUser.equals(toUser)) {
            throw new IllegalArgumentException("fromUser and toUser must not be the same user");
        }
        if (amount <= 0) {
            throw new IllegalArgumentException("amount must be strictly positive, got: " + amount);
        }
    }
}
