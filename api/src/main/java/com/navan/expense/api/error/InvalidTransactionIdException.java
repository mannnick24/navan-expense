package com.navan.expense.api.error;

public class InvalidTransactionIdException extends RuntimeException {

    public InvalidTransactionIdException(String id) {
        super("Invalid transaction id: " + id);
    }
}
