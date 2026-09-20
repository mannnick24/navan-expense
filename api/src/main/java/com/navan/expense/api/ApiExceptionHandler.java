package com.navan.expense.api;

import com.navan.expense.api.dto.ErrorResponse;
import com.navan.expense.api.dto.FieldErrorResponse;
import com.navan.expense.api.dto.MismatchResponse;
import com.navan.expense.api.dto.ParseErrorResponse;
import com.navan.expense.api.error.InvalidTransactionIdException;
import com.navan.expense.api.error.ItemizationMismatchException;
import com.navan.expense.api.error.OcrTextNotFoundException;
import com.navan.expense.api.error.ReceiptNotFoundException;
import com.navan.expense.api.error.TransactionNotFoundException;
import com.navan.expense.parser.ReceiptParseException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(ReceiptParseException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ParseErrorResponse parseFailed(ReceiptParseException ex) {
        return new ParseErrorResponse(
                "RECEIPT_PARSE_FAILED",
                ex.getMessage(),
                ex.getFields().stream().map(field -> new FieldErrorResponse(field.name(), field.reason())).toList()
        );
    }

    @ExceptionHandler(OcrTextNotFoundException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse ocrMissing(OcrTextNotFoundException ex) {
        return new ErrorResponse("OCR_TEXT_NOT_FOUND", ex.getMessage());
    }

    @ExceptionHandler(InvalidTransactionIdException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse invalidTransaction(InvalidTransactionIdException ex) {
        return new ErrorResponse("INVALID_TRANSACTION_ID", ex.getMessage());
    }

    @ExceptionHandler(ItemizationMismatchException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public MismatchResponse mismatch(ItemizationMismatchException ex) {
        return new MismatchResponse("ITEMIZATION_MISMATCH", ex.getGrandTotal(), ex.getItemsSum(), ex.getTaxesSum());
    }

    @ExceptionHandler({ReceiptNotFoundException.class, TransactionNotFoundException.class})
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse notFound(RuntimeException ex) {
        return new ErrorResponse("NOT_FOUND", ex.getMessage());
    }
}
