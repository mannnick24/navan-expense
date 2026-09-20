package com.navan.expense.api.dto;

import com.navan.expense.transaction.domain.ItemizeStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record TransactionResponse(
        UUID transactionId,
        UUID receiptId,
        String merchant,
        LocalDate date,
        String currency,
        BigDecimal grandTotal,
        List<TaxResponse> taxes,
        List<LineItemResponse> lineItems,
        ItemizeStatus itemizeStatus,
        String rawOcr
) {
}
