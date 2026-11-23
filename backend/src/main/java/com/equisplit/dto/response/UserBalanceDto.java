package com.equisplit.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class UserBalanceDto {

    @JsonProperty("userId")
    private UUID userId;

    private String name;
    private String email;
    private String balance;

    public UserBalanceDto() {
    }

    public UserBalanceDto(UUID userId, String name, String email, Long balancePaise) {
        this.userId = userId;
        this.name = name;
        this.email = email;
        this.balance = balancePaise != null ? balancePaise.toString() : "0";
    }

    public UserBalanceDto(UUID userId, String balance) {
        this.userId = userId;
        this.balance = balance;
    }

    @JsonProperty("user_id")
    public UUID getUserIdSnake() {
        return userId;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getBalance() {
        return balance;
    }

    public void setBalance(String balance) {
        this.balance = balance;
    }
}
