package com.equisplit.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

public class UpdateMemberRoleResponse {

    private String message;
    private MemberRoleInfo member;

    public UpdateMemberRoleResponse() {
    }

    public UpdateMemberRoleResponse(String message, UUID groupId, UUID userId, String role) {
        this.message = message;
        this.member = new MemberRoleInfo(groupId, userId, role);
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public MemberRoleInfo getMember() {
        return member;
    }

    public void setMember(MemberRoleInfo member) {
        this.member = member;
    }

    public static class MemberRoleInfo {
        @JsonProperty("group_id")
        private UUID groupId;

        @JsonProperty("user_id")
        private UUID userId;

        private String role;

        public MemberRoleInfo() {
        }

        public MemberRoleInfo(UUID groupId, UUID userId, String role) {
            this.groupId = groupId;
            this.userId = userId;
            this.role = role;
        }

        public UUID getGroupId() {
            return groupId;
        }

        public void setGroupId(UUID groupId) {
            this.groupId = groupId;
        }

        public UUID getUserId() {
            return userId;
        }

        public void setUserId(UUID userId) {
            this.userId = userId;
        }

        public String getRole() {
            return role;
        }

        public void setRole(String role) {
            this.role = role;
        }
    }
}
