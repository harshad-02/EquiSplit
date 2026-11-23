package com.equisplit.service.idempotency;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

@Component
public class RequestHasher {

    /**
     * Builds the exact canonical JSON string and computes its SHA-256 hex digest,
     * identical to Node.js crypto.createHash("sha256").update(JSON.stringify({fromUser, toUser, amount})).digest("hex").
     */
    public String hashSettlementRequest(UUID fromUser, UUID toUser, Long amount) {
        String canonicalJson = String.format(
                "{\"fromUser\":\"%s\",\"toUser\":\"%s\",\"amount\":\"%s\"}",
                fromUser.toString(),
                toUser.toString(),
                amount.toString()
        );

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(canonicalJson.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }
}
