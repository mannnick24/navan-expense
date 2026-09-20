package com.navan.expense.receipt.service;

import java.util.Optional;

public interface ReceiptTextResolver {

    Optional<String> resolve(String originalFilename);
}
