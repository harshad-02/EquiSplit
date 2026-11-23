package com.equisplit.dto.response;

import java.util.List;

public class GroupBalancesResponse {

    private List<UserBalanceDto> balances;

    public GroupBalancesResponse() {
    }

    public GroupBalancesResponse(List<UserBalanceDto> balances) {
        this.balances = balances;
    }

    public List<UserBalanceDto> getBalances() {
        return balances;
    }

    public void setBalances(List<UserBalanceDto> balances) {
        this.balances = balances;
    }
}
