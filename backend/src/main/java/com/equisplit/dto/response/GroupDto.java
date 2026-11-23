package com.equisplit.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class GroupDto {

    private UUID id;

    private String name;

    @JsonProperty("created_by")
    private UUID createdBy;

    @JsonProperty("created_at")
    private Instant createdAt;

    private String role;

    public GroupDto() {
    }

    public GroupDto(UUID id, String name, UUID createdBy, Instant createdAt) {
        this.id = id;
        this.name = name;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
    }

    public GroupDto(UUID id, String name, UUID createdBy, Instant createdAt, String role) {
        this.id = id;
        this.name = name;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
        this.role = role;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(UUID createdBy) {
        this.createdBy = createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }
}
