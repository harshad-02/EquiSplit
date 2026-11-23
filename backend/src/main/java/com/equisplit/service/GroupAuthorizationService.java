package com.equisplit.service;

import com.equisplit.entity.Group;
import com.equisplit.entity.GroupMember;
import com.equisplit.exception.ForbiddenException;
import com.equisplit.exception.NotFoundException;
import com.equisplit.repository.GroupMemberRepository;
import com.equisplit.repository.GroupRepository;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class GroupAuthorizationService {

    private final GroupRepository groupRepository;
    private final GroupMemberRepository groupMemberRepository;

    public GroupAuthorizationService(GroupRepository groupRepository,
                                     GroupMemberRepository groupMemberRepository) {
        this.groupRepository = groupRepository;
        this.groupMemberRepository = groupMemberRepository;
    }

    public GroupMember requireGroupMember(UUID userId, UUID groupId) {
        return groupMemberRepository.findByIdGroupIdAndIdUserId(groupId, userId)
                .orElseThrow(() -> new ForbiddenException("You are not a member of this group"));
    }

    public GroupMember requireGroupAdmin(UUID userId, UUID groupId, String failureMessage) {
        GroupMember member = requireGroupMember(userId, groupId);

        if (!"admin".equalsIgnoreCase(member.getRole())) {
            throw new ForbiddenException(failureMessage != null ? failureMessage : "Only group admins can perform this action");
        }

        return member;
    }

    public Group requireGroupOwner(UUID userId, UUID groupId) {
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new NotFoundException("Group not found"));

        if (!group.getCreatedBy().getId().equals(userId)) {
            throw new ForbiddenException("Only the group owner can transfer ownership");
        }

        return group;
    }
}
