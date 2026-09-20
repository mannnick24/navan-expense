package com.navan.expense.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record ItemOverride(
        @NotBlank String description,
        @NotNull BigDecimal amount,
        BigDecimal taxAmount,
        BigDecimal quantity
) {
}
