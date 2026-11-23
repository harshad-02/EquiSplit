package com.equisplit.dto.response;

public class RecordSettlementResponse {

    private String message;
    private RecordedSettlementDto settlement;

    public RecordSettlementResponse() {}

    public RecordSettlementResponse(String message, RecordedSettlementDto settlement) {
        this.message = message;
        this.settlement = settlement;
    }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public RecordedSettlementDto getSettlement() { return settlement; }
    public void setSettlement(RecordedSettlementDto settlement) { this.settlement = settlement; }
}
