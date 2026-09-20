package com.navan.expense.api.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record LineItemResponse(UUID id, String description, BigDecimal amount, BigDecimal taxAmount, BigDecimal quantity) {
}
