package com.navan.expense.transaction.service;

import com.navan.expense.transaction.domain.ItemizeStatus;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Collection;

@Component
public class DefaultReconciliationPolicy implements ReconciliationPolicy {

    @Override
    public boolean reconciles(BigDecimal grandTotal, Collection<BigDecimal> itemAmounts, Collection<BigDecimal> taxAmounts) {
        if (grandTotal == null || itemAmounts == null || itemAmounts.isEmpty()) {
            return false;
        }
        BigDecimal items = sum(itemAmounts);
        BigDecimal taxes = sum(taxAmounts);
        return eq(items.add(taxes), grandTotal) || eq(items, grandTotal);
    }

    @Override
    public ItemizeStatus status(BigDecimal grandTotal, Collection<BigDecimal> itemAmounts, Collection<BigDecimal> taxAmounts) {
        if (itemAmounts == null || itemAmounts.isEmpty() || hasNonPositiveAmount(grandTotal, itemAmounts, taxAmounts)) {
            return ItemizeStatus.NEEDS_REVIEW;
        }
        return reconciles(grandTotal, itemAmounts, taxAmounts) ? ItemizeStatus.COMPLETE : ItemizeStatus.NEEDS_REVIEW;
    }

    private static boolean hasNonPositiveAmount(
            BigDecimal grandTotal,
            Collection<BigDecimal> itemAmounts,
            Collection<BigDecimal> taxAmounts
    ) {
        if (grandTotal == null || grandTotal.compareTo(BigDecimal.ZERO) <= 0) {
            return true;
        }
        if (itemAmounts.stream().anyMatch(amount -> amount == null || amount.compareTo(BigDecimal.ZERO) <= 0)) {
            return true;
        }
        return taxAmounts != null && taxAmounts.stream().anyMatch(amount -> amount != null && amount.compareTo(BigDecimal.ZERO) < 0);
    }

    private static BigDecimal sum(Collection<BigDecimal> values) {
        if (values == null) {
            return BigDecimal.ZERO;
        }
        return values.stream().map(value -> value == null ? BigDecimal.ZERO : value).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static boolean eq(BigDecimal left, BigDecimal right) {
        return left.compareTo(right) == 0;
    }
}
