package com.equisplit.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class GroupMemberDto {

    private UUID id;

    @JsonProperty("user_id")
    private UUID userId;

    private String name;

    private String email;

    private String role;

    @JsonProperty("joined_at")
    private Instant joinedAt;

    public GroupMemberDto() {
    }

    public GroupMemberDto(UUID id, String name, String email, String role, Instant joinedAt) {
        this.id = id;
        this.userId = id;
        this.name = name;
        this.email = email;
        this.role = role;
        this.joinedAt = joinedAt;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
        this.userId = id;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
        this.id = userId;
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

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public Instant getJoinedAt() {
        return joinedAt;
    }

    public void setJoinedAt(Instant joinedAt) {
        this.joinedAt = joinedAt;
    }
}
