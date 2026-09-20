package com.navan.expense.receipt.service;

public final class ReceiptTextResolverFactory {

    public ReceiptTextResolver create() {
        return create(ReceiptTextResolverKind.FIXTURE);
    }

    public ReceiptTextResolver create(ReceiptTextResolverKind kind) {
        return switch (kind) {
            case FIXTURE -> new ClasspathReceiptTextResolver();
            case OCR -> new StubOcrReceiptTextResolver();
        };
    }
}
