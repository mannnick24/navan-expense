package com.navan.expense.api.error;

public class TransactionNotFoundException extends RuntimeException {

    public TransactionNotFoundException(String id) {
        super("Transaction not found: " + id);
    }
}
