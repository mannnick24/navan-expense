package com.navan.expense.api.error;

import java.math.BigDecimal;

public class ItemizationMismatchException extends RuntimeException {

    private final BigDecimal grandTotal;
    private final BigDecimal itemsSum;
    private final BigDecimal taxesSum;

    public ItemizationMismatchException(BigDecimal grandTotal, BigDecimal itemsSum, BigDecimal taxesSum) {
        super("Line items do not reconcile with the transaction total and stored taxes");
        this.grandTotal = grandTotal;
        this.itemsSum = itemsSum;
        this.taxesSum = taxesSum;
    }

    public BigDecimal getGrandTotal() {
        return grandTotal;
    }

    public BigDecimal getItemsSum() {
        return itemsSum;
    }

    public BigDecimal getTaxesSum() {
        return taxesSum;
    }
}
