package com.navan.expense.parser;

public interface ReceiptParser {

    ParsedReceipt parse(String rawText);
}
