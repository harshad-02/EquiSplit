package com.equisplit.service;

import com.equisplit.dto.request.AddMemberRequest;
import com.equisplit.dto.request.CreateGroupRequest;
import com.equisplit.dto.request.TransferOwnershipRequest;
import com.equisplit.dto.request.UpdateRoleRequest;
import com.equisplit.dto.response.AddMemberResponse;
import com.equisplit.dto.response.ApiResponse;
import com.equisplit.dto.response.CreateGroupResponse;
import com.equisplit.dto.response.GroupDto;
import com.equisplit.dto.response.GroupMemberDto;
import com.equisplit.dto.response.GroupMembersResponse;
import com.equisplit.dto.response.TransferOwnershipResponse;
import com.equisplit.dto.response.UpdateMemberRoleResponse;
import com.equisplit.dto.response.UserDto;
import com.equisplit.entity.Group;
import com.equisplit.entity.GroupMember;
import com.equisplit.entity.User;
import com.equisplit.exception.BadRequestException;
import com.equisplit.exception.ConflictException;
import com.equisplit.exception.NotFoundException;
import com.equisplit.repository.GroupMemberRepository;
import com.equisplit.repository.GroupRepository;
import com.equisplit.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class GroupService {

    private final GroupRepository groupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final UserRepository userRepository;
    private final GroupAuthorizationService groupAuthService;

    public GroupService(GroupRepository groupRepository,
                        GroupMemberRepository groupMemberRepository,
                        UserRepository userRepository,
                        GroupAuthorizationService groupAuthService) {
        this.groupRepository = groupRepository;
        this.groupMemberRepository = groupMemberRepository;
        this.userRepository = userRepository;
        this.groupAuthService = groupAuthService;
    }

    @Transactional(readOnly = true)
    public Map<String, List<GroupDto>> getMyGroups(UUID userId) {
        List<GroupMember> memberships = groupMemberRepository.findUserMembershipsWithGroup(userId);

        List<GroupDto> groupDtos = memberships.stream()
                .map(gm -> {
                    Group g = gm.getGroup();
                    return new GroupDto(
                            g.getId(),
                            g.getName(),
                            g.getCreatedBy().getId(),
                            g.getCreatedAt(),
                            gm.getRole()
                    );
                })
                .toList();

        return Map.of("groups", groupDtos);
    }

    @Transactional
    public CreateGroupResponse createGroup(UUID userId, CreateGroupRequest request) {
        User creator = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));

        Group group = new Group(request.getName().trim(), creator);
        Group savedGroup = groupRepository.save(group);

        GroupMember adminMember = new GroupMember(savedGroup, creator, "admin");
        groupMemberRepository.save(adminMember);

        GroupDto groupDto = new GroupDto(
                savedGroup.getId(),
                savedGroup.getName(),
                savedGroup.getCreatedBy().getId(),
                savedGroup.getCreatedAt()
        );

        return new CreateGroupResponse("Group created successfully", groupDto);
    }

    @Transactional(readOnly = true)
    public GroupMembersResponse getGroupMembers(UUID userId, UUID groupId) {
        groupAuthService.requireGroupMember(userId, groupId);

        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new NotFoundException("Group not found"));

        List<GroupMember> members = groupMemberRepository.findMembersByGroupIdWithUser(groupId);

        List<GroupMemberDto> memberDtos = members.stream()
                .map(gm -> new GroupMemberDto(
                        gm.getUser().getId(),
                        gm.getUser().getName(),
                        gm.getUser().getEmail(),
                        gm.getRole(),
                        gm.getJoinedAt()
                ))
                .toList();

        GroupDto groupDto = new GroupDto(
                group.getId(),
                group.getName(),
                group.getCreatedBy().getId(),
                group.getCreatedAt()
        );

        return new GroupMembersResponse(groupDto, memberDtos);
    }

    @Transactional
    public AddMemberResponse addMember(UUID requesterId, UUID groupId, AddMemberRequest request) {
        groupAuthService.requireGroupAdmin(requesterId, groupId, "Only group admins can add members");

        String cleanEmail = request.getEmail().trim().toLowerCase();

        User userToAdd = userRepository.findByEmailIgnoreCase(cleanEmail)
                .orElseThrow(() -> new NotFoundException("User not found"));

        if (groupMemberRepository.existsByIdGroupIdAndIdUserId(groupId, userToAdd.getId())) {
            throw new ConflictException("User is already a member");
        }

        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new NotFoundException("Group not found"));

        GroupMember newMember = new GroupMember(group, userToAdd, "member");
        groupMemberRepository.save(newMember);

        UserDto memberDto = new UserDto(
                userToAdd.getId(),
                userToAdd.getName(),
                userToAdd.getEmail(),
                "member"
        );

        return new AddMemberResponse("Member added successfully", memberDto);
    }

    @Transactional
    public ApiResponse removeMember(UUID requesterId, UUID groupId, UUID targetUserId) {
        groupAuthService.requireGroupAdmin(requesterId, groupId, "Only group admins can remove members");

        if (requesterId.equals(targetUserId)) {
            throw new BadRequestException("Group admin cannot remove themselves");
        }

        GroupMember memberToRemove = groupMemberRepository.findByIdGroupIdAndIdUserId(groupId, targetUserId)
                .orElseThrow(() -> new NotFoundException("User is not a member of this group"));

        if ("admin".equalsIgnoreCase(memberToRemove.getRole())) {
            long adminCount = groupMemberRepository.countByIdGroupIdAndRole(groupId, "admin");
            if (adminCount <= 1) {
                throw new BadRequestException("Group must have at least one admin");
            }
        }

        groupMemberRepository.delete(memberToRemove);

        return new ApiResponse("Member removed successfully");
    }

    @Transactional
    public UpdateMemberRoleResponse updateMemberRole(UUID requesterId, UUID groupId, UUID targetUserId, UpdateRoleRequest request) {
        groupAuthService.requireGroupAdmin(requesterId, groupId, "Only group admins can change member roles");

        if (requesterId.equals(targetUserId)) {
            throw new BadRequestException("You cannot change your own role");
        }

        GroupMember targetMember = groupMemberRepository.findByIdGroupIdAndIdUserId(groupId, targetUserId)
                .orElseThrow(() -> new NotFoundException("User is not a member of this group"));

        String newRole = request.getRole().trim().toLowerCase();

        if ("member".equals(newRole) && "admin".equalsIgnoreCase(targetMember.getRole())) {
            long adminCount = groupMemberRepository.countByIdGroupIdAndRole(groupId, "admin");
            if (adminCount <= 1) {
                throw new BadRequestException("Group must have at least one admin");
            }
        }

        targetMember.setRole(newRole);
        groupMemberRepository.save(targetMember);

        return new UpdateMemberRoleResponse("Member role updated successfully", groupId, targetUserId, newRole);
    }

    @Transactional
    public ApiResponse leaveGroup(UUID userId, UUID groupId) {
        GroupMember member = groupMemberRepository.findByIdGroupIdAndIdUserId(groupId, userId)
                .orElseThrow(() -> new NotFoundException("You are not a member of this group"));

        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new NotFoundException("Group not found"));

        if (group.getCreatedBy().getId().equals(userId)) {
            throw new BadRequestException("Group creator cannot leave the group");
        }

        if ("admin".equalsIgnoreCase(member.getRole())) {
            long otherAdmins = groupMemberRepository.countOtherAdmins(groupId, userId);
            if (otherAdmins == 0) {
                throw new BadRequestException("You are the only admin. Another admin must exist before you leave.");
            }
        }

        groupMemberRepository.delete(member);

        return new ApiResponse("You left the group successfully");
    }

    @Transactional
    public TransferOwnershipResponse transferOwnership(UUID requesterId, UUID groupId, TransferOwnershipRequest request) {
        Group group = groupAuthService.requireGroupOwner(requesterId, groupId);
        UUID newOwnerId = request.getUserId();

        if (requesterId.equals(newOwnerId)) {
            throw new BadRequestException("You are already the group owner");
        }

        GroupMember newOwnerMember = groupMemberRepository.findByIdGroupIdAndIdUserId(groupId, newOwnerId)
                .orElseThrow(() -> new BadRequestException("New owner must be a member of the group"));

        User newOwner = userRepository.findById(newOwnerId)
                .orElseThrow(() -> new NotFoundException("User not found"));

        group.setCreatedBy(newOwner);
        groupRepository.save(group);

        newOwnerMember.setRole("admin");
        groupMemberRepository.save(newOwnerMember);

        return new TransferOwnershipResponse("Group ownership transferred successfully", newOwnerId);
    }
}
