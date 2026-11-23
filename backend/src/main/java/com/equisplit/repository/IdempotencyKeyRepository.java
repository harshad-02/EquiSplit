package com.equisplit.repository;

import com.equisplit.entity.IdempotencyRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface IdempotencyKeyRepository extends JpaRepository<IdempotencyRecord, UUID> {

    Optional<IdempotencyRecord> findByUserIdAndGroupIdAndIdempotencyKey(
            UUID userId,
            UUID groupId,
            String idempotencyKey
    );
}
