package com.equisplit.service;

import com.equisplit.dto.request.AddMemberRequest;
import com.equisplit.dto.request.TransferOwnershipRequest;
import com.equisplit.dto.request.UpdateRoleRequest;
import com.equisplit.entity.Group;
import com.equisplit.entity.GroupMember;
import com.equisplit.entity.User;
import com.equisplit.exception.BadRequestException;
import com.equisplit.exception.ForbiddenException;
import com.equisplit.repository.GroupMemberRepository;
import com.equisplit.repository.GroupRepository;
import com.equisplit.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SecurityRbacComprehensiveTest {

    @Mock
    private GroupRepository groupRepository;
    @Mock
    private GroupMemberRepository groupMemberRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private GroupAuthorizationService groupAuthService;

    private GroupService groupService;

    private UUID groupId;
    private UUID ownerId;
    private UUID adminId;
    private UUID memberId;
    private UUID outsiderId;

    private User owner;
    private User admin;
    private User member;
    private Group group;

    @BeforeEach
    void setUp() {
        groupService = new GroupService(groupRepository, groupMemberRepository, userRepository, groupAuthService);

        groupId = UUID.randomUUID();
        ownerId = UUID.randomUUID();
        adminId = UUID.randomUUID();
        memberId = UUID.randomUUID();
        outsiderId = UUID.randomUUID();

        owner = new User("Owner", "owner@example.com", "hash", "user");
        owner.setId(ownerId);
        admin = new User("Admin", "admin@example.com", "hash", "user");
        admin.setId(adminId);
        member = new User("Member", "member@example.com", "hash", "user");
        member.setId(memberId);

        group = new Group("Private Group", owner);
        group.setId(groupId);
    }

    @Test
    @DisplayName("RBAC: Group creator/owner cannot leave the group directly without transferring ownership")
    void testGroupCreatorCannotLeaveGroup() {
        GroupMember ownerMember = new GroupMember(group, owner, "admin");

        when(groupMemberRepository.findByIdGroupIdAndIdUserId(groupId, ownerId))
                .thenReturn(Optional.of(ownerMember));
        when(groupRepository.findById(groupId))
                .thenReturn(Optional.of(group));

        assertThatThrownBy(() -> groupService.leaveGroup(ownerId, groupId))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Group creator cannot leave the group");

        verify(groupMemberRepository, never()).delete(any());
    }

    @Test
    @DisplayName("RBAC: Sole group admin cannot leave the group if no other admin exists")
    void testSoleAdminCannotLeaveGroup() {
        User otherCreator = new User("Creator", "c@example.com", "hash", "user");
        otherCreator.setId(UUID.randomUUID());
        Group otherGroup = new Group("Another Group", otherCreator);
        otherGroup.setId(groupId);

        GroupMember adminMember = new GroupMember(otherGroup, admin, "admin");

        when(groupMemberRepository.findByIdGroupIdAndIdUserId(groupId, adminId))
                .thenReturn(Optional.of(adminMember));
        when(groupRepository.findById(groupId))
                .thenReturn(Optional.of(otherGroup));
        when(groupMemberRepository.countOtherAdmins(groupId, adminId))
                .thenReturn(0L);

        assertThatThrownBy(() -> groupService.leaveGroup(adminId, groupId))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("You are the only admin. Another admin must exist before you leave.");

        verify(groupMemberRepository, never()).delete(any());
    }

    @Test
    @DisplayName("RBAC: Non-admin member attempting to add a member throws 403 Forbidden")
    void testNonAdminCannotAddMember() {
        doThrow(new ForbiddenException("Only group admins can add members"))
                .when(groupAuthService).requireGroupAdmin(memberId, groupId, "Only group admins can add members");

        AddMemberRequest req = new AddMemberRequest("newuser@example.com");

        assertThatThrownBy(() -> groupService.addMember(memberId, groupId, req))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("Only group admins can add members");

        verify(groupMemberRepository, never()).save(any());
    }

    @Test
    @DisplayName("RBAC: Non-admin member attempting to remove another member throws 403 Forbidden")
    void testNonAdminCannotRemoveMember() {
        doThrow(new ForbiddenException("Only group admins can remove members"))
                .when(groupAuthService).requireGroupAdmin(memberId, groupId, "Only group admins can remove members");

        assertThatThrownBy(() -> groupService.removeMember(memberId, groupId, adminId))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("Only group admins can remove members");

        verify(groupMemberRepository, never()).delete(any());
    }

    @Test
    @DisplayName("RBAC: Non-owner attempting to transfer ownership throws 403 Forbidden")
    void testNonOwnerCannotTransferOwnership() {
        doThrow(new ForbiddenException("Only the group owner can transfer ownership"))
                .when(groupAuthService).requireGroupOwner(adminId, groupId);

        TransferOwnershipRequest req = new TransferOwnershipRequest(memberId);

        assertThatThrownBy(() -> groupService.transferOwnership(adminId, groupId, req))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("Only the group owner can transfer ownership");

        verify(groupRepository, never()).save(any());
    }

    @Test
    @DisplayName("RBAC: Admin cannot demote themselves to regular member")
    void testAdminCannotDemoteSelf() {
        UpdateRoleRequest req = new UpdateRoleRequest("member");

        assertThatThrownBy(() -> groupService.updateMemberRole(adminId, groupId, adminId, req))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("You cannot change your own role");

        verify(groupMemberRepository, never()).save(any());
    }
}
