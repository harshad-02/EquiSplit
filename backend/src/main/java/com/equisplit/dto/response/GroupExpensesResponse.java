package com.equisplit.dto.response;

import java.util.List;

public class GroupExpensesResponse {

    private List<ExpenseDetailDto> expenses;

    public GroupExpensesResponse() {
    }

    public GroupExpensesResponse(List<ExpenseDetailDto> expenses) {
        this.expenses = expenses;
    }

    public List<ExpenseDetailDto> getExpenses() {
        return expenses;
    }

    public void setExpenses(List<ExpenseDetailDto> expenses) {
        this.expenses = expenses;
    }
}
