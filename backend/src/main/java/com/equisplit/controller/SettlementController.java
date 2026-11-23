package com.equisplit.controller;

import com.equisplit.dto.request.RecordSettlementRequest;
import com.equisplit.dto.response.SuggestedSettlementsResponse;
import com.equisplit.security.UserPrincipal;
import com.equisplit.service.settlement.SettlementRecordService;
import com.equisplit.service.settlement.SettlementResult;
import com.equisplit.service.settlement.SettlementSuggestionService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/groups")
public class SettlementController {

    private final SettlementSuggestionService settlementSuggestionService;
    private final SettlementRecordService settlementRecordService;

    public SettlementController(
            SettlementSuggestionService settlementSuggestionService,
            SettlementRecordService settlementRecordService
    ) {
        this.settlementSuggestionService = settlementSuggestionService;
        this.settlementRecordService = settlementRecordService;
    }

    @GetMapping("/{groupId}/settlements/suggested")
    public ResponseEntity<SuggestedSettlementsResponse> getSuggestedSettlements(
            @PathVariable UUID groupId,
            @AuthenticationPrincipal UserPrincipal userPrincipal
    ) {
        SuggestedSettlementsResponse response =
                settlementSuggestionService.getSuggestedSettlements(userPrincipal.getId(), groupId);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{groupId}/settlements")
    public ResponseEntity<?> recordSettlement(
            @PathVariable UUID groupId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody(required = false) RecordSettlementRequest request,
            @AuthenticationPrincipal UserPrincipal userPrincipal
    ) {
        SettlementResult result = settlementRecordService.recordSettlement(
                userPrincipal.getId(),
                groupId,
                idempotencyKey,
                request
        );
        return ResponseEntity.status(result.statusCode()).body(result.responseBody());
    }
}
