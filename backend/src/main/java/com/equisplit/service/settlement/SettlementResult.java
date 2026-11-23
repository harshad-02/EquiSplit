package com.equisplit.service.settlement;

import java.util.UUID;

public record SettlementResult(
        int statusCode,
        Object responseBody
) {
    public static SettlementResult created(Object body) {
        return new SettlementResult(201, body);
    }

    public static SettlementResult replay(int status, Object body) {
        return new SettlementResult(status, body);
    }
}
