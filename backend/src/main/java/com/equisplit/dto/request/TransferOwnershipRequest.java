package com.equisplit.dto.request;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public class TransferOwnershipRequest {

    @NotNull(message = "New owner userId is required")
    private UUID userId;

    public TransferOwnershipRequest() {
    }

    public TransferOwnershipRequest(UUID userId) {
        this.userId = userId;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }
}
