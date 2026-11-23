package com.equisplit.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ExpenseSplitDto {

    @JsonProperty("userId")
    private UUID userId;

    @JsonProperty("amount")
    private Long amount;

    @JsonProperty("percentage")
    private BigDecimal percentage;

    @JsonProperty("userName")
    private String userName;

    public ExpenseSplitDto() {
    }

    public ExpenseSplitDto(UUID userId, Long amount) {
        this.userId = userId;
        this.amount = amount;
    }

    public ExpenseSplitDto(UUID userId, Long amount, BigDecimal percentage) {
        this.userId = userId;
        this.amount = amount;
        this.percentage = percentage;
    }

    public ExpenseSplitDto(UUID userId, Long amount, BigDecimal percentage, String userName) {
        this.userId = userId;
        this.amount = amount;
        this.percentage = percentage;
        this.userName = userName;
    }

    // Both userId and user_id for frontend/test compatibility
    @JsonProperty("user_id")
    public UUID getUserIdSnake() {
        return userId;
    }

    @JsonProperty("user_name")
    public String getUserNameSnake() {
        return userName;
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

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }
}
