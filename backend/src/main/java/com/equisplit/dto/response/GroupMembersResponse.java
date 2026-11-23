package com.equisplit.dto.response;

import java.util.List;

public class GroupMembersResponse {

    private GroupDto group;
    private List<GroupMemberDto> members;

    public GroupMembersResponse() {
    }

    public GroupMembersResponse(GroupDto group, List<GroupMemberDto> members) {
        this.group = group;
        this.members = members;
    }

    public GroupDto getGroup() {
        return group;
    }

    public void setGroup(GroupDto group) {
        this.group = group;
    }

    public List<GroupMemberDto> getMembers() {
        return members;
    }

    public void setMembers(List<GroupMemberDto> members) {
        this.members = members;
    }
}
