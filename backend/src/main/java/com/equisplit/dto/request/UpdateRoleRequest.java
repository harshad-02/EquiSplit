package com.equisplit.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public class UpdateRoleRequest {

    @NotBlank(message = "Role is required")
    @Pattern(regexp = "^(admin|member)$", message = "Role must be either admin or member")
    private String role;

    public UpdateRoleRequest() {
    }

    public UpdateRoleRequest(String role) {
        this.role = role;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }
}
