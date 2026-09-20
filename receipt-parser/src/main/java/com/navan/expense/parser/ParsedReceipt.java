package com.navan.expense.parser;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record ParsedReceipt(
        String merchant,
        LocalDate date,
        String currency,
        BigDecimal grandTotal,
        List<ParsedTax> taxes,
        List<ParsedLineItem> lineItems
) {
}
