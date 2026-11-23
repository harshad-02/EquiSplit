package com.equisplit.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class ExpenseDetailDto {

    private UUID id;
    private String description;

    @JsonProperty("total_amount")
    private String totalAmount;

    @JsonProperty("split_type")
    private String splitType;

    @JsonProperty("paid_by")
    private UUID paidBy;

    @JsonProperty("paid_by_name")
    private String paidByName;

    @JsonProperty("created_at")
    private Instant createdAt;

    private List<ExpenseSplitDto> splits;

    public ExpenseDetailDto() {
    }

    public ExpenseDetailDto(UUID id, String description, Long totalAmount, String splitType,
                            UUID paidBy, String paidByName, Instant createdAt, List<ExpenseSplitDto> splits) {
        this.id = id;
        this.description = description;
        this.totalAmount = totalAmount != null ? totalAmount.toString() : null;
        this.splitType = splitType;
        this.paidBy = paidBy;
        this.paidByName = paidByName;
        this.createdAt = createdAt;
        this.splits = splits;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(String totalAmount) {
        this.totalAmount = totalAmount;
    }

    public String getSplitType() {
        return splitType;
    }

    public void setSplitType(String splitType) {
        this.splitType = splitType;
    }

    public UUID getPaidBy() {
        return paidBy;
    }

    public void setPaidBy(UUID paidBy) {
        this.paidBy = paidBy;
    }

    public String getPaidByName() {
        return paidByName;
    }

    public void setPaidByName(String paidByName) {
        this.paidByName = paidByName;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public List<ExpenseSplitDto> getSplits() {
        return splits;
    }

    public void setSplits(List<ExpenseSplitDto> splits) {
        this.splits = splits;
    }
}
