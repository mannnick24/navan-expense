package com.navan.expense.api.error;

import java.util.UUID;

public class ReceiptNotFoundException extends RuntimeException {

    public ReceiptNotFoundException(UUID id) {
        super("Receipt not found: " + id);
    }
}
