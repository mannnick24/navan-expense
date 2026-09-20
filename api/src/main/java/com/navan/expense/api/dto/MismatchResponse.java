package com.navan.expense.api.dto;

import java.math.BigDecimal;

public record MismatchResponse(String error, BigDecimal grandTotal, BigDecimal itemsSum, BigDecimal taxesSum) {
}
