package com.equisplit.dto.request;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public class RecordSettlementRequest {

    @NotNull(message = "fromUser, toUser and amount are required")
    private UUID fromUser;

    @NotNull(message = "fromUser, toUser and amount are required")
    private UUID toUser;

    @NotNull(message = "fromUser, toUser and amount are required")
    private Object amount;

    public RecordSettlementRequest() {}

    public RecordSettlementRequest(UUID fromUser, UUID toUser, Object amount) {
        this.fromUser = fromUser;
        this.toUser = toUser;
        this.amount = amount;
    }

    public UUID getFromUser() { return fromUser; }
    public void setFromUser(UUID fromUser) { this.fromUser = fromUser; }

    public UUID getToUser() { return toUser; }
    public void setToUser(UUID toUser) { this.toUser = toUser; }

    public Object getAmount() { return amount; }
    public void setAmount(Object amount) { this.amount = amount; }
}
