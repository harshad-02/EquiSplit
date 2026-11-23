package com.equisplit.service.lock;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class AdvisoryLockService {

    private static final Logger log = LoggerFactory.getLogger(AdvisoryLockService.class);

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * Acquires a PostgreSQL transaction-scoped advisory lock using hashtext(:key).
     * The lock is automatically released by PostgreSQL when the current transaction completes (commit or rollback).
     *
     * @param key The string key to hash and lock (e.g. groupId string)
     */
    public void acquireTransactionLock(String key) {
        log.debug("Acquiring PostgreSQL advisory transaction lock for key: {}", key);
        entityManager.createNativeQuery("SELECT pg_advisory_xact_lock(hashtext(:key))")
                .setParameter("key", key)
                .getSingleResult();
        log.debug("Successfully acquired PostgreSQL advisory transaction lock for key: {}", key);
    }
}
