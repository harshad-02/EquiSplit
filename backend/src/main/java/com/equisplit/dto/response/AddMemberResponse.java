package com.equisplit.dto.response;

public class AddMemberResponse {

    private String message;
    private UserDto member;

    public AddMemberResponse() {
    }

    public AddMemberResponse(String message, UserDto member) {
        this.message = message;
        this.member = member;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public UserDto getMember() {
        return member;
    }

    public void setMember(UserDto member) {
        this.member = member;
    }
}
