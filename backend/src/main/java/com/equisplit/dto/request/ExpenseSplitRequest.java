package com.equisplit.dto.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public class ExpenseSplitRequest {

    @NotNull(message = "Each split must contain a userId")
    @JsonProperty("userId")
    @JsonAlias({"user_id", "userId"})
    private UUID userId;

    private Long amount;

    private BigDecimal percentage;

    public ExpenseSplitRequest() {
    }

    public ExpenseSplitRequest(UUID userId) {
        this.userId = userId;
    }

    public ExpenseSplitRequest(UUID userId, Long amount) {
        this.userId = userId;
        this.amount = amount;
    }

    public ExpenseSplitRequest(UUID userId, BigDecimal percentage) {
        this.userId = userId;
        this.percentage = percentage;
    }

    public ExpenseSplitRequest(UUID userId, Long amount, BigDecimal percentage) {
        this.userId = userId;
        this.amount = amount;
        this.percentage = percentage;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public Long getAmount() {
        return amount;
    }

    public void setAmount(Long amount) {
        this.amount = amount;
    }

    public BigDecimal getPercentage() {
        return percentage;
    }

    public void setPercentage(BigDecimal percentage) {
        this.percentage = percentage;
    }
}
