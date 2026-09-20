package com.navan.expense.api.error;

public class InvalidReceiptFileException extends RuntimeException {

    public InvalidReceiptFileException(String message) {
        super(message);
    }
}
