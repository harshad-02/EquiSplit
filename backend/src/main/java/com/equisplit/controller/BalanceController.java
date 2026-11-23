package com.equisplit.controller;

import com.equisplit.dto.response.GroupBalancesResponse;
import com.equisplit.security.UserPrincipal;
import com.equisplit.service.BalanceService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/groups")
public class BalanceController {

    private final BalanceService balanceService;

    public BalanceController(BalanceService balanceService) {
        this.balanceService = balanceService;
    }

    @GetMapping("/{groupId}/balances")
    public ResponseEntity<GroupBalancesResponse> getGroupBalances(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID groupId) {
        GroupBalancesResponse response = balanceService.getGroupBalances(principal.getId(), groupId);
        return ResponseEntity.ok(response);
    }
}
