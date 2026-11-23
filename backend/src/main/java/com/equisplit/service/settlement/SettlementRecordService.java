package com.equisplit.service.settlement;

import com.equisplit.dto.request.RecordSettlementRequest;
import com.equisplit.dto.response.RecordSettlementResponse;
import com.equisplit.dto.response.RecordedSettlementDto;
import com.equisplit.entity.AuditLog;
import com.equisplit.entity.Expense;
import com.equisplit.entity.Group;
import com.equisplit.entity.IdempotencyRecord;
import com.equisplit.entity.Settlement;
import com.equisplit.entity.User;
import com.equisplit.exception.BadRequestException;
import com.equisplit.exception.ConflictException;
import com.equisplit.exception.NotFoundException;
import com.equisplit.repository.AuditLogRepository;
import com.equisplit.repository.ExpenseRepository;
import com.equisplit.repository.GroupMemberRepository;
import com.equisplit.repository.GroupRepository;
import com.equisplit.repository.IdempotencyKeyRepository;
import com.equisplit.repository.SettlementRepository;
import com.equisplit.repository.SettlementRepository.PreviousSettlementSummary;
import com.equisplit.repository.UserRepository;
import com.equisplit.service.GroupAuthorizationService;
import com.equisplit.service.balance.GroupBalanceCalculator;
import com.equisplit.service.idempotency.RequestHasher;
import com.equisplit.service.lock.AdvisoryLockService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class SettlementRecordService {

    private static final Logger log = LoggerFactory.getLogger(SettlementRecordService.class);

    private final GroupAuthorizationService groupAuthService;
    private final AdvisoryLockService advisoryLockService;
    private final RequestHasher requestHasher;
    private final ExpenseRepository expenseRepository;
    private final SettlementRepository settlementRepository;
    private final IdempotencyKeyRepository idempotencyKeyRepository;
    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;
    private final GroupRepository groupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final GroupBalanceCalculator balanceCalculator;
    private final ObjectMapper objectMapper;

    public SettlementRecordService(
            GroupAuthorizationService groupAuthService,
            AdvisoryLockService advisoryLockService,
            RequestHasher requestHasher,
            ExpenseRepository expenseRepository,
            SettlementRepository settlementRepository,
            IdempotencyKeyRepository idempotencyKeyRepository,
            AuditLogRepository auditLogRepository,
            UserRepository userRepository,
            GroupRepository groupRepository,
            GroupMemberRepository groupMemberRepository,
            GroupBalanceCalculator balanceCalculator,
            ObjectMapper objectMapper
    ) {
        this.groupAuthService = groupAuthService;
        this.advisoryLockService = advisoryLockService;
        this.requestHasher = requestHasher;
        this.expenseRepository = expenseRepository;
        this.settlementRepository = settlementRepository;
        this.idempotencyKeyRepository = idempotencyKeyRepository;
        this.auditLogRepository = auditLogRepository;
        this.userRepository = userRepository;
        this.groupRepository = groupRepository;
        this.groupMemberRepository = groupMemberRepository;
        this.balanceCalculator = balanceCalculator;
        this.objectMapper = objectMapper;
    }

    /**
     * Records a financial settlement within a transaction protected by a PostgreSQL advisory lock,
     * verifying idempotency, financial balances, and persisting the settlement, audit log, and idempotency key.
     */
    @Transactional(rollbackFor = Exception.class)
    public SettlementResult recordSettlement(
            UUID requesterId,
            UUID groupId,
            String idempotencyKeyHeader,
            RecordSettlementRequest request
    ) {
        // 1. Validate Idempotency-Key header presence and length
        if (idempotencyKeyHeader == null || idempotencyKeyHeader.trim().isEmpty()) {
            throw new BadRequestException("Idempotency-Key header is required");
        }
        String idempotencyKey = idempotencyKeyHeader.trim();
        if (idempotencyKey.length() > 255) {
            throw new BadRequestException("Idempotency-Key must not exceed 255 characters");
        }

        // 2. Validate request payload required fields
        if (request == null || request.getFromUser() == null || request.getToUser() == null || request.getAmount() == null) {
            throw new BadRequestException("fromUser, toUser and amount are required");
        }

        UUID fromUser = request.getFromUser();
        UUID toUser = request.getToUser();

        if (fromUser.equals(toUser)) {
            throw new BadRequestException("fromUser and toUser must be different");
        }

        // Parse amount
        Long settlementAmount = parseAmount(request.getAmount());
        if (settlementAmount <= 0) {
            throw new BadRequestException("Amount must be greater than zero");
        }

        // 3. Acquire PostgreSQL transaction-scoped advisory lock for this group
        advisoryLockService.acquireTransactionLock(groupId.toString());

        // 4. Compute request SHA-256 hash
        String currentHash = requestHasher.hashSettlementRequest(fromUser, toUser, settlementAmount);

        // 5. Check Idempotency Record
        Optional<IdempotencyRecord> existingKeyOpt =
                idempotencyKeyRepository.findByUserIdAndGroupIdAndIdempotencyKey(requesterId, groupId, idempotencyKey);

        if (existingKeyOpt.isPresent()) {
            IdempotencyRecord existingRecord = existingKeyOpt.get();
            if (!existingRecord.getRequestHash().equals(currentHash)) {
                throw new ConflictException("Idempotency-Key has already been used for a different request");
            }

            // Return cached response body and status
            try {
                Object cachedBody = objectMapper.readValue(existingRecord.getResponseBody(), Object.class);
                return SettlementResult.replay(existingRecord.getResponseStatus(), cachedBody);
            } catch (JsonProcessingException e) {
                log.error("Failed to deserialize cached idempotency response: ", e);
                throw new IllegalStateException("Failed to deserialize idempotency cached response", e);
            }
        }

        // 6. Verify membership: Requester must be member of group (throws 403 Forbidden)
        groupAuthService.requireGroupMember(requesterId, groupId);

        // Verify group exists
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new NotFoundException("Group not found"));

        // Verify both fromUser and toUser are members of the group
        boolean fromUserIsMember = groupMemberRepository.existsByIdGroupIdAndIdUserId(groupId, fromUser);
        boolean toUserIsMember = groupMemberRepository.existsByIdGroupIdAndIdUserId(groupId, toUser);

        if (!fromUserIsMember || !toUserIsMember) {
            throw new BadRequestException("Both users must be members of the group");
        }

        // 7. Calculate raw expense balances
        List<Expense> expenses = expenseRepository.findByGroupIdWithSplitsAndUsersOrderByCreatedAtDesc(groupId);
        Map<UUID, Long> expenseBalances = balanceCalculator.calculateFromExpenses(expenses);

        long fromBalance = expenseBalances.getOrDefault(fromUser, 0L);
        long toBalance = expenseBalances.getOrDefault(toUser, 0L);

        // Financial validation: fromUser must be debtor (< 0) and toUser must be creditor (> 0)
        if (fromBalance >= 0 || toBalance <= 0) {
            throw new BadRequestException("Invalid settlement direction");
        }

        // 8. Query previous settlements between these two users in this group
        PreviousSettlementSummary prevSettlements =
                settlementRepository.findPreviousSettlementsBetweenUsers(groupId, fromUser, toUser);

        long paidAmount = prevSettlements != null && prevSettlements.getPaid_amount() != null
                ? prevSettlements.getPaid_amount() : 0L;
        long reverseAmount = prevSettlements != null && prevSettlements.getReverse_amount() != null
                ? prevSettlements.getReverse_amount() : 0L;

        long netPreviousSettlement = paidAmount - reverseAmount;
        long originalDebt = -fromBalance; // positive amount debtor owes overall
        long outstandingDebt = originalDebt - netPreviousSettlement;

        if (outstandingDebt <= 0) {
            throw new BadRequestException("No outstanding debt exists between these users");
        }

        if (settlementAmount > outstandingDebt) {
            throw new BadRequestException("Settlement amount exceeds outstanding debt");
        }

        // 9. Load User entities
        User requester = userRepository.findById(requesterId)
                .orElseThrow(() -> new NotFoundException("User not found"));
        User fromUserEntity = userRepository.findById(fromUser)
                .orElseThrow(() -> new NotFoundException("Debtor user not found"));
        User toUserEntity = userRepository.findById(toUser)
                .orElseThrow(() -> new NotFoundException("Creditor user not found"));

        // 10. Persist Settlement
        Settlement settlement = new Settlement(group, fromUserEntity, toUserEntity, settlementAmount);
        settlement = settlementRepository.save(settlement);

        // 11. Build Response DTO
        RecordedSettlementDto settlementDto = new RecordedSettlementDto(
                settlement.getId(),
                groupId,
                fromUser,
                toUser,
                String.valueOf(settlementAmount),
                settlement.getCreatedAt()
        );
        RecordSettlementResponse responseBody = new RecordSettlementResponse(
                "Settlement recorded successfully",
                settlementDto
        );

        // 12. Create Audit Log
        String auditMetadata = String.format(
                "{\"groupId\":\"%s\",\"fromUser\":\"%s\",\"toUser\":\"%s\",\"amount\":\"%s\"}",
                groupId, fromUser, toUser, settlementAmount
        );
        AuditLog auditLog = new AuditLog(
                requester,
                "SETTLEMENT_RECORDED",
                "settlement",
                settlement.getId(),
                auditMetadata
        );
        auditLogRepository.save(auditLog);

        // 13. Save Idempotency Record
        try {
            String serializedResponseBody = objectMapper.writeValueAsString(responseBody);
            IdempotencyRecord idempotencyRecord = new IdempotencyRecord(
                    requester,
                    group,
                    idempotencyKey,
                    currentHash,
                    201,
                    serializedResponseBody
            );
            idempotencyKeyRepository.save(idempotencyRecord);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize response body for idempotency store: ", e);
            throw new IllegalStateException("Failed to serialize settlement response", e);
        }

        return SettlementResult.created(responseBody);
    }

    private Long parseAmount(Object rawAmount) {
        if (rawAmount == null) {
            throw new BadRequestException("Amount must be a valid integer in paise");
        }
        try {
            if (rawAmount instanceof Number number) {
                return number.longValue();
            }
            String str = rawAmount.toString().trim();
            return Long.parseLong(str);
        } catch (NumberFormatException e) {
            throw new BadRequestException("Amount must be a valid integer in paise");
        }
    }
}
