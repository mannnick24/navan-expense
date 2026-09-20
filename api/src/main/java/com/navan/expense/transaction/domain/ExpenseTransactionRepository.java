package com.navan.expense.transaction.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ExpenseTransactionRepository extends JpaRepository<ExpenseTransaction, UUID> {
}
