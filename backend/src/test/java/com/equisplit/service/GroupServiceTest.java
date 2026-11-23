package com.equisplit.service;

import com.equisplit.dto.request.AddMemberRequest;
import com.equisplit.dto.request.CreateGroupRequest;
import com.equisplit.dto.request.TransferOwnershipRequest;
import com.equisplit.dto.request.UpdateRoleRequest;
import com.equisplit.dto.response.AddMemberResponse;
import com.equisplit.dto.response.ApiResponse;
import com.equisplit.dto.response.CreateGroupResponse;
import com.equisplit.dto.response.GroupDto;
import com.equisplit.dto.response.GroupMembersResponse;
import com.equisplit.dto.response.TransferOwnershipResponse;
import com.equisplit.dto.response.UpdateMemberRoleResponse;
import com.equisplit.entity.Group;
import com.equisplit.entity.GroupMember;
import com.equisplit.entity.User;
import com.equisplit.exception.BadRequestException;
import com.equisplit.exception.ConflictException;
import com.equisplit.exception.ForbiddenException;
import com.equisplit.exception.NotFoundException;
import com.equisplit.repository.GroupMemberRepository;
import com.equisplit.repository.GroupRepository;
import com.equisplit.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GroupServiceTest {

    @Mock
    private GroupRepository groupRepository;

    @Mock
    private GroupMemberRepository groupMemberRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private GroupAuthorizationService groupAuthService;

    private GroupService groupService;

    private User adminUser;
    private User regularUser;
    private Group testGroup;
    private UUID groupId;
    private UUID adminId;
    private UUID regularUserId;

    @BeforeEach
    void setUp() {
        groupService = new GroupService(groupRepository, groupMemberRepository, userRepository, groupAuthService);

        adminId = UUID.randomUUID();
        adminUser = new User("Admin User", "admin@example.com", "hash", "user");
        adminUser.setId(adminId);

        regularUserId = UUID.randomUUID();
        regularUser = new User("Regular User", "member@example.com", "hash", "user");
        regularUser.setId(regularUserId);

        groupId = UUID.randomUUID();
        testGroup = new Group("Test Group", adminUser);
        testGroup.setId(groupId);
        testGroup.setCreatedAt(Instant.now());
    }

    @Test
    @DisplayName("createGroup creates group and automatically assigns creator as admin")
    void testCreateGroupSuccess() {
        when(userRepository.findById(adminId)).thenReturn(Optional.of(adminUser));
        when(groupRepository.save(any(Group.class))).thenAnswer(inv -> {
            Group g = inv.getArgument(0);
            g.setId(groupId);
            g.setCreatedAt(Instant.now());
            return g;
        });

        CreateGroupRequest request = new CreateGroupRequest("Weekend Trip");
        CreateGroupResponse response = groupService.createGroup(adminId, request);

        assertNotNull(response);
        assertEquals("Group created successfully", response.getMessage());
        assertEquals("Weekend Trip", response.getGroup().getName());
        assertEquals(adminId, response.getGroup().getCreatedBy());

        verify(groupMemberRepository).save(argThat(gm ->
                "admin".equals(gm.getRole()) &&
                gm.getUser().getId().equals(adminId)
        ));
    }

    @Test
    @DisplayName("getGroupMembers returns members when requester is a group member")
    void testGetGroupMembersSuccess() {
        when(groupRepository.findById(groupId)).thenReturn(Optional.of(testGroup));

        GroupMember gmAdmin = new GroupMember(testGroup, adminUser, "admin");
        gmAdmin.setJoinedAt(Instant.now());
        GroupMember gmRegular = new GroupMember(testGroup, regularUser, "member");
        gmRegular.setJoinedAt(Instant.now());

        when(groupMemberRepository.findMembersByGroupIdWithUser(groupId)).thenReturn(List.of(gmAdmin, gmRegular));

        GroupMembersResponse response = groupService.getGroupMembers(adminId, groupId);

        assertNotNull(response);
        assertEquals(2, response.getMembers().size());
        assertEquals("admin", response.getMembers().get(0).getRole());
        assertEquals("member", response.getMembers().get(1).getRole());
        verify(groupAuthService).requireGroupMember(adminId, groupId);
    }

    @Test
    @DisplayName("getGroupMembers throws ForbiddenException when requester is not a member")
    void testGetGroupMembersForbidden() {
        doThrow(new ForbiddenException("You are not a member of this group"))
                .when(groupAuthService).requireGroupMember(regularUserId, groupId);

        assertThrows(ForbiddenException.class, () -> groupService.getGroupMembers(regularUserId, groupId));
    }

    @Test
    @DisplayName("addMember adds member with default 'member' role")
    void testAddMemberSuccess() {
        User newUser = new User("New Member", "new@example.com", "hash", "user");
        newUser.setId(UUID.randomUUID());

        when(userRepository.findByEmailIgnoreCase("new@example.com")).thenReturn(Optional.of(newUser));
        when(groupMemberRepository.existsByIdGroupIdAndIdUserId(groupId, newUser.getId())).thenReturn(false);
        when(groupRepository.findById(groupId)).thenReturn(Optional.of(testGroup));

        AddMemberRequest request = new AddMemberRequest("new@example.com");
        AddMemberResponse response = groupService.addMember(adminId, groupId, request);

        assertNotNull(response);
        assertEquals("Member added successfully", response.getMessage());
        assertEquals(newUser.getId(), response.getMember().getId());
        assertEquals("member", response.getMember().getRole());

        verify(groupAuthService).requireGroupAdmin(adminId, groupId, "Only group admins can add members");
        verify(groupMemberRepository).save(argThat(gm -> "member".equals(gm.getRole())));
    }

    @Test
    @DisplayName("addMember throws ConflictException when user is already in group")
    void testAddMemberAlreadyExists() {
        when(userRepository.findByEmailIgnoreCase("member@example.com")).thenReturn(Optional.of(regularUser));
        when(groupMemberRepository.existsByIdGroupIdAndIdUserId(groupId, regularUserId)).thenReturn(true);

        AddMemberRequest request = new AddMemberRequest("member@example.com");
        assertThrows(ConflictException.class, () -> groupService.addMember(adminId, groupId, request));
    }

    @Test
    @DisplayName("removeMember prevents admin from removing themselves")
    void testRemoveMemberSelfThrows() {
        assertThrows(BadRequestException.class, () -> groupService.removeMember(adminId, groupId, adminId));
    }

    @Test
    @DisplayName("removeMember prevents removing the last admin")
    void testRemoveLastAdminThrows() {
        GroupMember targetAdmin = new GroupMember(testGroup, regularUser, "admin");

        when(groupMemberRepository.findByIdGroupIdAndIdUserId(groupId, regularUserId)).thenReturn(Optional.of(targetAdmin));
        when(groupMemberRepository.countByIdGroupIdAndRole(groupId, "admin")).thenReturn(1L);

        assertThrows(BadRequestException.class, () -> groupService.removeMember(adminId, groupId, regularUserId));
    }

    @Test
    @DisplayName("updateMemberRole prevents changing own role")
    void testUpdateRoleSelfThrows() {
        UpdateRoleRequest request = new UpdateRoleRequest("member");
        assertThrows(BadRequestException.class, () -> groupService.updateMemberRole(adminId, groupId, adminId, request));
    }

    @Test
    @DisplayName("updateMemberRole prevents demoting the last admin")
    void testDemoteLastAdminThrows() {
        GroupMember targetAdmin = new GroupMember(testGroup, regularUser, "admin");

        when(groupMemberRepository.findByIdGroupIdAndIdUserId(groupId, regularUserId)).thenReturn(Optional.of(targetAdmin));
        when(groupMemberRepository.countByIdGroupIdAndRole(groupId, "admin")).thenReturn(1L);

        UpdateRoleRequest request = new UpdateRoleRequest("member");
        assertThrows(BadRequestException.class, () -> groupService.updateMemberRole(adminId, groupId, regularUserId, request));
    }

    @Test
    @DisplayName("leaveGroup prevents group creator from leaving")
    void testCreatorCannotLeave() {
        GroupMember creatorMember = new GroupMember(testGroup, adminUser, "admin");
        when(groupMemberRepository.findByIdGroupIdAndIdUserId(groupId, adminId)).thenReturn(Optional.of(creatorMember));
        when(groupRepository.findById(groupId)).thenReturn(Optional.of(testGroup));

        assertThrows(BadRequestException.class, () -> groupService.leaveGroup(adminId, groupId));
    }

    @Test
    @DisplayName("transferOwnership successfully transfers ownership to existing member")
    void testTransferOwnershipSuccess() {
        when(groupAuthService.requireGroupOwner(adminId, groupId)).thenReturn(testGroup);
        GroupMember targetMember = new GroupMember(testGroup, regularUser, "member");
        when(groupMemberRepository.findByIdGroupIdAndIdUserId(groupId, regularUserId)).thenReturn(Optional.of(targetMember));
        when(userRepository.findById(regularUserId)).thenReturn(Optional.of(regularUser));

        TransferOwnershipRequest request = new TransferOwnershipRequest(regularUserId);
        TransferOwnershipResponse response = groupService.transferOwnership(adminId, groupId, request);

        assertNotNull(response);
        assertEquals("Group ownership transferred successfully", response.getMessage());
        assertEquals(regularUserId, response.getNewOwnerId());

        verify(groupRepository).save(argThat(g -> g.getCreatedBy().getId().equals(regularUserId)));
        verify(groupMemberRepository).save(argThat(gm -> "admin".equals(gm.getRole())));
    }
}
