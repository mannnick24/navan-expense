package com.navan.expense.parser;

import java.util.List;

public class ReceiptParseException extends RuntimeException {

    private final List<FieldError> fields;

    public ReceiptParseException(List<FieldError> fields) {
        super("OCR text is missing required fields or contains invalid values");
        this.fields = List.copyOf(fields);
    }

    public List<FieldError> getFields() {
        return fields;
    }

    public record FieldError(String name, String reason) {
    }
}
