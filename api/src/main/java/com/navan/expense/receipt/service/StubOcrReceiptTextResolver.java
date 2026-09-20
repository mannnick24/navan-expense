package com.navan.expense.receipt.service;

import java.nio.file.Path;
import java.util.Optional;

/**
 * Placeholder for a vendor OCR adapter (Vision, Textract, etc.).
 * A real implementation would read {@code storedPath} and return extracted text.
 */
public final class StubOcrReceiptTextResolver implements ReceiptTextResolver {

    @Override
    public Optional<String> resolve(String originalFilename, Path storedPath) {
        throw new UnsupportedOperationException(
                "Vendor OCR is not wired; use app.ocr.kind=fixture or implement ReceiptTextResolver"
        );
    }
}
