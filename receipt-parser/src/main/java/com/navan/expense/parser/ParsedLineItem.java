package com.navan.expense.parser;

import java.math.BigDecimal;

public record ParsedLineItem(String description, BigDecimal amount, BigDecimal taxAmount, BigDecimal quantity) {
}
