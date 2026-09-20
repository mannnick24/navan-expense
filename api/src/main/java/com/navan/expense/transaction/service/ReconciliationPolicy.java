package com.navan.expense.transaction.service;

import com.navan.expense.transaction.domain.ItemizeStatus;

import java.math.BigDecimal;
import java.util.Collection;

public interface ReconciliationPolicy {

    boolean reconciles(BigDecimal grandTotal, Collection<BigDecimal> itemAmounts, Collection<BigDecimal> taxAmounts);

    ItemizeStatus status(BigDecimal grandTotal, Collection<BigDecimal> itemAmounts, Collection<BigDecimal> taxAmounts);
}
