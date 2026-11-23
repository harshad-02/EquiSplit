package com.equisplit.dto.response;

import java.util.List;

public class CreateExpenseResponse {

    private String message;
    private ExpenseDto expense;
    private List<ExpenseSplitDto> splits;

    public CreateExpenseResponse() {
    }

    public CreateExpenseResponse(String message, ExpenseDto expense, List<ExpenseSplitDto> splits) {
        this.message = message;
        this.expense = expense;
        this.splits = splits;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public ExpenseDto getExpense() {
        return expense;
    }

    public void setExpense(ExpenseDto expense) {
        this.expense = expense;
    }

    public List<ExpenseSplitDto> getSplits() {
        return splits;
    }

    public void setSplits(List<ExpenseSplitDto> splits) {
        this.splits = splits;
    }
}
