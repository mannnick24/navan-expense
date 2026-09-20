package com.navan.expense.api.dto;

import java.util.List;

public record ParseErrorResponse(String error, String message, List<FieldErrorResponse> fields) {
}
