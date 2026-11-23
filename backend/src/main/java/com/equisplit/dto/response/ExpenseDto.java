package com.equisplit.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.UUID;

public class ExpenseDto {

    private UUID id;

    @JsonProperty("group_id")
    private UUID groupId;

    @JsonProperty("paid_by")
    private UUID paidBy;

    private String description;

    @JsonProperty("total_amount")
    private String totalAmount;

    @JsonProperty("split_type")
    private String splitType;

    @JsonProperty("created_at")
    private Instant createdAt;

    public ExpenseDto() {
    }

    public ExpenseDto(UUID id, UUID groupId, UUID paidBy, String description, Long totalAmount, String splitType, Instant createdAt) {
        this.id = id;
        this.groupId = groupId;
        this.paidBy = paidBy;
        this.description = description;
        this.totalAmount = totalAmount != null ? totalAmount.toString() : null;
        this.splitType = splitType;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getGroupId() {
        return groupId;
    }

    public void setGroupId(UUID groupId) {
        this.groupId = groupId;
    }

    public UUID getPaidBy() {
        return paidBy;
    }

    public void setPaidBy(UUID paidBy) {
        this.paidBy = paidBy;
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

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
