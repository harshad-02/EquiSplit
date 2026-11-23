package com.equisplit.controller;

import com.equisplit.config.SecurityConfig;
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
import com.equisplit.entity.User;
import com.equisplit.exception.GlobalExceptionHandler;
import com.equisplit.security.CustomUserDetailsService;
import com.equisplit.security.JwtAuthenticationEntryPoint;
import com.equisplit.security.JwtAuthenticationFilter;
import com.equisplit.security.JwtTokenProvider;
import com.equisplit.security.UserPrincipal;
import com.equisplit.service.GroupService;
import com.equisplit.util.CookieUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(GroupController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtAuthenticationEntryPoint.class, CookieUtil.class, GlobalExceptionHandler.class})
@AutoConfigureMockMvc
class GroupControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private GroupService groupService;

    @MockBean
    private JwtTokenProvider tokenProvider;

    @MockBean
    private CustomUserDetailsService userDetailsService;

    private UUID userId;
    private String token;
    private User user;
    private UserPrincipal principal;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        token = "mock.auth.jwt.token";
        user = new User("Auth User", "auth@example.com", "hash", "user");
        user.setId(userId);
        principal = UserPrincipal.create(user);

        when(tokenProvider.validateToken(token)).thenReturn(true);
        when(tokenProvider.getUserIdFromToken(token)).thenReturn(userId);
        when(userDetailsService.loadUserById(userId)).thenReturn(principal);
    }

    @Test
    @DisplayName("GET /api/groups returns 200 and list of user groups")
    void testGetMyGroups() throws Exception {
        GroupDto groupDto = new GroupDto(UUID.randomUUID(), "Test Group", userId, Instant.now(), "admin");
        when(groupService.getMyGroups(userId)).thenReturn(Map.of("groups", List.of(groupDto)));

        mockMvc.perform(get("/api/groups")
                        .cookie(new Cookie("token", token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.groups").isArray())
                .andExpect(jsonPath("$.groups[0].name").value("Test Group"))
                .andExpect(jsonPath("$.groups[0].role").value("admin"));
    }

    @Test
    @DisplayName("POST /api/groups creates group and returns 201 Created")
    void testCreateGroup() throws Exception {
        UUID groupId = UUID.randomUUID();
        GroupDto groupDto = new GroupDto(groupId, "Trip 2026", userId, Instant.now());
        CreateGroupResponse response = new CreateGroupResponse("Group created successfully", groupDto);

        when(groupService.createGroup(eq(userId), any(CreateGroupRequest.class))).thenReturn(response);

        CreateGroupRequest request = new CreateGroupRequest("Trip 2026");

        mockMvc.perform(post("/api/groups")
                        .cookie(new Cookie("token", token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("Group created successfully"))
                .andExpect(jsonPath("$.group.id").value(groupId.toString()))
                .andExpect(jsonPath("$.group.name").value("Trip 2026"));
    }

    @Test
    @DisplayName("GET /api/groups/{groupId}/members returns 200 with members and group info")
    void testGetGroupMembers() throws Exception {
        UUID groupId = UUID.randomUUID();
        GroupDto groupDto = new GroupDto(groupId, "Trip 2026", userId, Instant.now());
        GroupMemberDto memberDto = new GroupMemberDto(userId, "Auth User", "auth@example.com", "admin", Instant.now());

        GroupMembersResponse response = new GroupMembersResponse(groupDto, List.of(memberDto));
        when(groupService.getGroupMembers(userId, groupId)).thenReturn(response);

        mockMvc.perform(get("/api/groups/{groupId}/members", groupId)
                        .cookie(new Cookie("token", token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.group.id").value(groupId.toString()))
                .andExpect(jsonPath("$.members[0].name").value("Auth User"))
                .andExpect(jsonPath("$.members[0].role").value("admin"));
    }

    @Test
    @DisplayName("POST /api/groups/{groupId}/members adds member and returns 201 Created")
    void testAddMember() throws Exception {
        UUID groupId = UUID.randomUUID();
        UUID newMemberId = UUID.randomUUID();
        UserDto memberDto = new UserDto(newMemberId, "New Member", "new@example.com", "member");
        AddMemberResponse response = new AddMemberResponse("Member added successfully", memberDto);

        when(groupService.addMember(eq(userId), eq(groupId), any(AddMemberRequest.class))).thenReturn(response);

        AddMemberRequest request = new AddMemberRequest("new@example.com");

        mockMvc.perform(post("/api/groups/{groupId}/members", groupId)
                        .cookie(new Cookie("token", token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("Member added successfully"))
                .andExpect(jsonPath("$.member.email").value("new@example.com"));
    }

    @Test
    @DisplayName("DELETE /api/groups/{groupId}/members/{targetId} removes member with 200 OK")
    void testRemoveMember() throws Exception {
        UUID groupId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();

        when(groupService.removeMember(userId, groupId, targetId)).thenReturn(new ApiResponse("Member removed successfully"));

        mockMvc.perform(delete("/api/groups/{groupId}/members/{targetId}", groupId, targetId)
                        .cookie(new Cookie("token", token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Member removed successfully"));
    }

    @Test
    @DisplayName("PUT /api/groups/{groupId}/members/{targetId}/role updates member role")
    void testUpdateMemberRole() throws Exception {
        UUID groupId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();

        UpdateMemberRoleResponse response = new UpdateMemberRoleResponse("Member role updated successfully", groupId, targetId, "admin");
        when(groupService.updateMemberRole(eq(userId), eq(groupId), eq(targetId), any(UpdateRoleRequest.class))).thenReturn(response);

        UpdateRoleRequest request = new UpdateRoleRequest("admin");

        mockMvc.perform(put("/api/groups/{groupId}/members/{targetId}/role", groupId, targetId)
                        .cookie(new Cookie("token", token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Member role updated successfully"))
                .andExpect(jsonPath("$.member.role").value("admin"));
    }

    @Test
    @DisplayName("DELETE /api/groups/{groupId}/leave allows member to leave with 200 OK")
    void testLeaveGroup() throws Exception {
        UUID groupId = UUID.randomUUID();

        when(groupService.leaveGroup(userId, groupId)).thenReturn(new ApiResponse("You left the group successfully"));

        mockMvc.perform(delete("/api/groups/{groupId}/leave", groupId)
                        .cookie(new Cookie("token", token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("You left the group successfully"));
    }

    @Test
    @DisplayName("PUT /api/groups/{groupId}/ownership transfers ownership with 200 OK")
    void testTransferOwnership() throws Exception {
        UUID groupId = UUID.randomUUID();
        UUID newOwnerId = UUID.randomUUID();

        TransferOwnershipResponse response = new TransferOwnershipResponse("Group ownership transferred successfully", newOwnerId);
        when(groupService.transferOwnership(eq(userId), eq(groupId), any(TransferOwnershipRequest.class))).thenReturn(response);

        TransferOwnershipRequest request = new TransferOwnershipRequest(newOwnerId);

        mockMvc.perform(put("/api/groups/{groupId}/ownership", groupId)
                        .cookie(new Cookie("token", token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Group ownership transferred successfully"))
                .andExpect(jsonPath("$.newOwnerId").value(newOwnerId.toString()));
    }

    @Test
    @DisplayName("Unauthenticated request to /api/groups is rejected with 401")
    void testUnauthenticatedGroupAccess() throws Exception {
        mockMvc.perform(get("/api/groups"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Authentication required"));
    }
}
