package com.equisplit.repository;

import com.equisplit.entity.Settlement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SettlementRepository extends JpaRepository<Settlement, UUID> {

    List<Settlement> findByGroupIdOrderByCreatedAtDesc(UUID groupId);

    List<Settlement> findByGroupId(UUID groupId);

    @Query(value = """
        SELECT
            COALESCE(SUM(CASE WHEN from_user = :fromUser AND to_user = :toUser THEN amount ELSE 0 END), 0) AS paid_amount,
            COALESCE(SUM(CASE WHEN from_user = :toUser AND to_user = :fromUser THEN amount ELSE 0 END), 0) AS reverse_amount
        FROM settlements
        WHERE group_id = :groupId
        AND ((from_user = :fromUser AND to_user = :toUser) OR (from_user = :toUser AND to_user = :fromUser))
        """, nativeQuery = true)
    PreviousSettlementSummary findPreviousSettlementsBetweenUsers(
            @Param("groupId") UUID groupId,
            @Param("fromUser") UUID fromUser,
            @Param("toUser") UUID toUser
    );

    interface PreviousSettlementSummary {
        Long getPaid_amount();
        Long getReverse_amount();
    }
}
