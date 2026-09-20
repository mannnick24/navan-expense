package com.navan.expense.api.dto;

import java.math.BigDecimal;

public record TaxResponse(String name, BigDecimal rate, BigDecimal amount) {
}
