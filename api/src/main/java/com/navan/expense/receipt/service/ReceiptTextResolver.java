package com.navan.expense.receipt.service;

import java.nio.file.Path;
import java.util.Optional;

public interface ReceiptTextResolver {

    Optional<String> resolve(String originalFilename, Path storedPath);
}
