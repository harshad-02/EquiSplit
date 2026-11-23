package com.equisplit.dto.response;

public class CreateGroupResponse {

    private String message;
    private GroupDto group;

    public CreateGroupResponse() {
    }

    public CreateGroupResponse(String message, GroupDto group) {
        this.message = message;
        this.group = group;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public GroupDto getGroup() {
        return group;
    }

    public void setGroup(GroupDto group) {
        this.group = group;
    }
}
