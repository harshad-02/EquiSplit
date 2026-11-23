package com.equisplit.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class UserDto {

    private UUID id;

    private UUID userId;

    private String name;

    private String email;

    private String role;

    @JsonProperty("created_at")
    private Instant createdAt;

    public UserDto() {
    }

    public UserDto(UUID id, String name, String email, String role) {
        this.id = id;
        this.userId = id;
        this.name = name;
        this.email = email;
        this.role = role;
    }

    public UserDto(UUID id, String name, String email, String role, Instant createdAt) {
        this.id = id;
        this.userId = id;
        this.name = name;
        this.email = email;
        this.role = role;
        this.createdAt = createdAt;
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

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
