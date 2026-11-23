package com.equisplit.controller;

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
import com.equisplit.security.UserPrincipal;
import com.equisplit.service.GroupService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/groups")
public class GroupController {

    private final GroupService groupService;

    public GroupController(GroupService groupService) {
        this.groupService = groupService;
    }

    @GetMapping
    public ResponseEntity<Map<String, List<GroupDto>>> getMyGroups(@AuthenticationPrincipal UserPrincipal principal) {
        Map<String, List<GroupDto>> response = groupService.getMyGroups(principal.getId());
        return ResponseEntity.ok(response);
    }

    @PostMapping
    public ResponseEntity<CreateGroupResponse> createGroup(@AuthenticationPrincipal UserPrincipal principal,
                                                          @Valid @RequestBody CreateGroupRequest request) {
        CreateGroupResponse response = groupService.createGroup(principal.getId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{groupId}/members")
    public ResponseEntity<GroupMembersResponse> getGroupMembers(@AuthenticationPrincipal UserPrincipal principal,
                                                               @PathVariable UUID groupId) {
        GroupMembersResponse response = groupService.getGroupMembers(principal.getId(), groupId);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{groupId}/members")
    public ResponseEntity<AddMemberResponse> addMember(@AuthenticationPrincipal UserPrincipal principal,
                                                       @PathVariable UUID groupId,
                                                       @Valid @RequestBody AddMemberRequest request) {
        AddMemberResponse response = groupService.addMember(principal.getId(), groupId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @DeleteMapping("/{groupId}/members/{userId}")
    public ResponseEntity<ApiResponse> removeMember(@AuthenticationPrincipal UserPrincipal principal,
                                                    @PathVariable UUID groupId,
                                                    @PathVariable UUID userId) {
        ApiResponse response = groupService.removeMember(principal.getId(), groupId, userId);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{groupId}/members/{userId}/role")
    public ResponseEntity<UpdateMemberRoleResponse> updateMemberRole(@AuthenticationPrincipal UserPrincipal principal,
                                                                    @PathVariable UUID groupId,
                                                                    @PathVariable UUID userId,
                                                                    @Valid @RequestBody UpdateRoleRequest request) {
        UpdateMemberRoleResponse response = groupService.updateMemberRole(principal.getId(), groupId, userId, request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{groupId}/leave")
    public ResponseEntity<ApiResponse> leaveGroup(@AuthenticationPrincipal UserPrincipal principal,
                                                  @PathVariable UUID groupId) {
        ApiResponse response = groupService.leaveGroup(principal.getId(), groupId);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{groupId}/ownership")
    public ResponseEntity<TransferOwnershipResponse> transferOwnership(@AuthenticationPrincipal UserPrincipal principal,
                                                                      @PathVariable UUID groupId,
                                                                      @Valid @RequestBody TransferOwnershipRequest request) {
        TransferOwnershipResponse response = groupService.transferOwnership(principal.getId(), groupId, request);
        return ResponseEntity.ok(response);
    }
}
