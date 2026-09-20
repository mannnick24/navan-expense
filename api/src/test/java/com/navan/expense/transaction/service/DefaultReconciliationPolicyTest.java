package com.navan.expense.transaction.service;

import com.navan.expense.transaction.domain.ItemizeStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultReconciliationPolicyTest {

    private final ReconciliationPolicy policy = new DefaultReconciliationPolicy();

    @Test
    void completeWhenNetItemsPlusTaxEqualTotal() {
        assertThat(policy.status(new BigDecimal("17.85"), List.of(new BigDecimal("3.50"), new BigDecimal("8.90"), new BigDecimal("2.60")), List.of(new BigDecimal("2.85"))))
                .isEqualTo(ItemizeStatus.COMPLETE);
        assertThat(policy.reconciles(new BigDecimal("17.85"), List.of(new BigDecimal("3.50"), new BigDecimal("8.90"), new BigDecimal("2.60")), List.of(new BigDecimal("2.85"))))
                .isTrue();
    }

    @Test
    void completeWhenGrossItemsEqualTotal() {
        assertThat(policy.status(new BigDecimal("17.85"), List.of(new BigDecimal("17.85")), List.of(new BigDecimal("2.85"))))
                .isEqualTo(ItemizeStatus.COMPLETE);
    }

    @Test
    void needsReviewWhenNoLineItems() {
        assertThat(policy.status(new BigDecimal("24.00"), List.of(), List.of(new BigDecimal("3.83"))))
                .isEqualTo(ItemizeStatus.NEEDS_REVIEW);
        assertThat(policy.reconciles(new BigDecimal("24.00"), List.of(), List.of(new BigDecimal("3.83"))))
                .isFalse();
    }

    @Test
    void needsReviewWhenItemsDoNotMatchTotal() {
        assertThat(policy.status(new BigDecimal("18.50"), List.of(new BigDecimal("4.00"), new BigDecimal("6.00")), List.of(new BigDecimal("1.90"))))
                .isEqualTo(ItemizeStatus.NEEDS_REVIEW);
        assertThat(policy.reconciles(new BigDecimal("18.50"), List.of(new BigDecimal("4.00"), new BigDecimal("6.00")), List.of(new BigDecimal("1.90"))))
                .isFalse();
    }

    @Test
    void completeWhenHundredNetItemsMatchSubtotalAndTotal() {
        List<BigDecimal> items = hundredOnes();
        assertThat(policy.status(new BigDecimal("119.00"), items, List.of(new BigDecimal("19.00"))))
                .isEqualTo(ItemizeStatus.COMPLETE);
        assertThat(policy.reconciles(new BigDecimal("119.00"), items, List.of(new BigDecimal("19.00"))))
                .isTrue();
    }

    @Test
    void needsReviewWhenHundredItemsDoNotMatchPrintedSubtotalAndTotal() {
        List<BigDecimal> items = hundredOnes();
        assertThat(policy.status(new BigDecimal("250.00"), items, List.of(new BigDecimal("19.00"))))
                .isEqualTo(ItemizeStatus.NEEDS_REVIEW);
        assertThat(policy.reconciles(new BigDecimal("250.00"), items, List.of(new BigDecimal("19.00"))))
                .isFalse();
    }

    @Test
    void completeWhenLargeNetItemsMatchTotal() {
        List<BigDecimal> items = List.of(
                new BigDecimal("1000000000.00"),
                new BigDecimal("2000000000.00"),
                new BigDecimal("500000000.00")
        );
        assertThat(policy.status(new BigDecimal("4165000000.00"), items, List.of(new BigDecimal("665000000.00"))))
                .isEqualTo(ItemizeStatus.COMPLETE);
    }

    @Test
    void needsReviewWhenAmountsAreZeroEvenIfMathBalances() {
        assertThat(policy.status(BigDecimal.ZERO, List.of(BigDecimal.ZERO, BigDecimal.ZERO), List.of(BigDecimal.ZERO)))
                .isEqualTo(ItemizeStatus.NEEDS_REVIEW);
        assertThat(policy.status(new BigDecimal("3.50"), List.of(new BigDecimal("3.50"), BigDecimal.ZERO), List.of()))
                .isEqualTo(ItemizeStatus.NEEDS_REVIEW);
    }

    @Test
    void needsReviewWhenAmountsAreNegativeEvenIfMathBalances() {
        assertThat(policy.status(
                new BigDecimal("-7.14"),
                List.of(new BigDecimal("-5.00"), new BigDecimal("-1.00")),
                List.of(new BigDecimal("-1.14"))
        )).isEqualTo(ItemizeStatus.NEEDS_REVIEW);
    }

    private static List<BigDecimal> hundredOnes() {
        return java.util.Collections.nCopies(100, new BigDecimal("1.00"));
    }
}
