package com.navan.expense.parser;

public final class ReceiptParserFactory {

    public ReceiptParser create() {
        return create(ReceiptParserKind.REGEX);
    }

    public ReceiptParser create(ReceiptParserKind kind) {
        return switch (kind) {
            case REGEX -> new RegexReceiptParser();
        };
    }
}
