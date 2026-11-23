package com.equisplit.dto.response;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class RecordedSettlementDto {

    private UUID id;

    @JsonProperty("group_id")
    private UUID groupId;

    @JsonProperty("from_user")
    private UUID fromUser;

    @JsonProperty("to_user")
    private UUID toUser;

    private String amount;

    @JsonProperty("created_at")
    private Instant createdAt;

    public RecordedSettlementDto() {}

    public RecordedSettlementDto(UUID id, UUID groupId, UUID fromUser, UUID toUser, String amount, Instant createdAt) {
        this.id = id;
        this.groupId = groupId;
        this.fromUser = fromUser;
        this.toUser = toUser;
        this.amount = amount;
        this.createdAt = createdAt;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getGroupId() { return groupId; }
    public void setGroupId(UUID groupId) { this.groupId = groupId; }

    public UUID getFromUser() { return fromUser; }
    public void setFromUser(UUID fromUser) { this.fromUser = fromUser; }

    public UUID getToUser() { return toUser; }
    public void setToUser(UUID toUser) { this.toUser = toUser; }

    public String getAmount() { return amount; }
    public void setAmount(String amount) { this.amount = amount; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    // Aliases for camelCase / snake_case compatibility
    @JsonGetter("groupId")
    public UUID getGroupIdCamel() { return groupId; }

    @JsonGetter("fromUser")
    public UUID getFromUserCamel() { return fromUser; }

    @JsonGetter("toUser")
    public UUID getToUserCamel() { return toUser; }

    @JsonGetter("from")
    public UUID getFrom() { return fromUser; }

    @JsonGetter("to")
    public UUID getTo() { return toUser; }
}
