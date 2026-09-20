package com.navan.expense.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record PatchItemsRequest(@NotEmpty List<@Valid ItemOverride> items) {
}
