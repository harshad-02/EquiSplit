package com.equisplit.controller;

import com.equisplit.dto.request.CreateExpenseRequest;
import com.equisplit.dto.response.CreateExpenseResponse;
import com.equisplit.dto.response.GroupExpensesResponse;
import com.equisplit.security.UserPrincipal;
import com.equisplit.service.ExpenseService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api")
public class ExpenseController {

    private final ExpenseService expenseService;

    public ExpenseController(ExpenseService expenseService) {
        this.expenseService = expenseService;
    }

    @PostMapping("/groups/{groupId}/expenses")
    public ResponseEntity<CreateExpenseResponse> createGroupExpense(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID groupId,
            @Valid @RequestBody CreateExpenseRequest request) {
        CreateExpenseResponse response = expenseService.createExpense(principal.getId(), groupId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/expenses")
    public ResponseEntity<CreateExpenseResponse> createExpense(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody CreateExpenseRequest request) {
        CreateExpenseResponse response = expenseService.createExpense(principal.getId(), null, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/groups/{groupId}/expenses")
    public ResponseEntity<GroupExpensesResponse> getGroupExpenses(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID groupId) {
        GroupExpensesResponse response = expenseService.getGroupExpenses(principal.getId(), groupId);
        return ResponseEntity.ok(response);
    }
}
