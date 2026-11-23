package com.equisplit.dto.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public class CreateExpenseRequest {

    @JsonProperty("groupId")
    @JsonAlias({"group_id", "groupId"})
    private UUID groupId;

    @JsonProperty("paidBy")
    @JsonAlias({"paid_by", "paidBy"})
    private UUID paidBy;

    @NotBlank(message = "Description is required")
    @Size(max = 255, message = "Description must not exceed 255 characters")
    private String description;

    @NotNull(message = "totalAmount is required")
    @Positive(message = "totalAmount must be greater than zero")
    @JsonProperty("totalAmount")
    @JsonAlias({"total_amount", "totalAmount"})
    private Long totalAmount;

    @NotBlank(message = "splitType is required")
    @JsonProperty("splitType")
    @JsonAlias({"split_type", "splitType"})
    private String splitType;

    @NotNull(message = "splits are required")
    @Size(min = 1, message = "At least one split is required")
    @Valid
    private List<ExpenseSplitRequest> splits;

    public CreateExpenseRequest() {
    }

    public CreateExpenseRequest(String description, Long totalAmount, String splitType, List<ExpenseSplitRequest> splits) {
        this.description = description;
        this.totalAmount = totalAmount;
        this.splitType = splitType;
        this.splits = splits;
    }

    public CreateExpenseRequest(UUID groupId, String description, Long totalAmount, String splitType, List<ExpenseSplitRequest> splits) {
        this.groupId = groupId;
        this.description = description;
        this.totalAmount = totalAmount;
        this.splitType = splitType;
        this.splits = splits;
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

    public Long getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(Long totalAmount) {
        this.totalAmount = totalAmount;
    }

    public String getSplitType() {
        return splitType;
    }

    public void setSplitType(String splitType) {
        this.splitType = splitType;
    }

    public List<ExpenseSplitRequest> getSplits() {
        return splits;
    }

    public void setSplits(List<ExpenseSplitRequest> splits) {
        this.splits = splits;
    }
}
