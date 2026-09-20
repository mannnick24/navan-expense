package com.navan.expense.parser;

import java.math.BigDecimal;

public record ParsedTax(String name, BigDecimal rate, BigDecimal amount) {
}
