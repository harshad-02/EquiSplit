package com.equisplit.dto.response;

import java.util.List;

public class SuggestedSettlementsResponse {
    private List<SuggestedSettlementDto> settlements;

    public SuggestedSettlementsResponse() {}

    public SuggestedSettlementsResponse(List<SuggestedSettlementDto> settlements) {
        this.settlements = settlements;
    }

    public List<SuggestedSettlementDto> getSettlements() {
        return settlements;
    }

    public void setSettlements(List<SuggestedSettlementDto> settlements) {
        this.settlements = settlements;
    }
}
