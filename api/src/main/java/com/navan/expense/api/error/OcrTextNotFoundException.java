package com.navan.expense.api.error;

public class OcrTextNotFoundException extends RuntimeException {

    public OcrTextNotFoundException() {
        super("No OCR fixture text for the uploaded filename");
    }
}
