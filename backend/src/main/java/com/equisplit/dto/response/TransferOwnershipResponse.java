package com.equisplit.dto.response;

import java.util.UUID;

public class TransferOwnershipResponse {

    private String message;
    private UUID newOwnerId;

    public TransferOwnershipResponse() {
    }

    public TransferOwnershipResponse(String message, UUID newOwnerId) {
        this.message = message;
        this.newOwnerId = newOwnerId;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public UUID getNewOwnerId() {
        return newOwnerId;
    }

    public void setNewOwnerId(UUID newOwnerId) {
        this.newOwnerId = newOwnerId;
    }
}
