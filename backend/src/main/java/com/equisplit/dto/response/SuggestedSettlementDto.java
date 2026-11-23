package com.equisplit.dto.response;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class SuggestedSettlementDto {

    @JsonProperty("from")
    private UUID from;

    @JsonProperty("to")
    private UUID to;

    @JsonProperty("amount")
    private String amount;

    @JsonProperty("fromName")
    private String fromName;

    @JsonProperty("toName")
    private String toName;

    public SuggestedSettlementDto() {}

    public SuggestedSettlementDto(UUID from, UUID to, String amount, String fromName, String toName) {
        this.from = from;
        this.to = to;
        this.amount = amount;
        this.fromName = fromName;
        this.toName = toName;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private UUID from;
        private UUID to;
        private String amount;
        private String fromName;
        private String toName;

        public Builder from(UUID from) {
            this.from = from;
            return this;
        }

        public Builder to(UUID to) {
            this.to = to;
            return this;
        }

        public Builder amount(String amount) {
            this.amount = amount;
            return this;
        }

        public Builder fromName(String fromName) {
            this.fromName = fromName;
            return this;
        }

        public Builder toName(String toName) {
            this.toName = toName;
            return this;
        }

        public SuggestedSettlementDto build() {
            return new SuggestedSettlementDto(from, to, amount, fromName, toName);
        }
    }

    public UUID getFrom() {
        return from;
    }

    public void setFrom(UUID from) {
        this.from = from;
    }

    public UUID getTo() {
        return to;
    }

    public void setTo(UUID to) {
        this.to = to;
    }

    public String getAmount() {
        return amount;
    }

    public void setAmount(String amount) {
        this.amount = amount;
    }

    public String getFromName() {
        return fromName;
    }

    public void setFromName(String fromName) {
        this.fromName = fromName;
    }

    public String getToName() {
        return toName;
    }

    public void setToName(String toName) {
        this.toName = toName;
    }

    @JsonGetter("fromUser")
    public UUID getFromUser() {
        return from;
    }

    @JsonGetter("toUser")
    public UUID getToUser() {
        return to;
    }

    @JsonGetter("from_user")
    public UUID getFromUserSnake() {
        return from;
    }

    @JsonGetter("to_user")
    public UUID getToUserSnake() {
        return to;
    }

    @JsonGetter("from_name")
    public String getFromNameSnake() {
        return fromName;
    }

    @JsonGetter("to_name")
    public String getToNameSnake() {
        return toName;
    }
}