package com.checkout.backend.savings.expense.repository;

import com.checkout.backend.savings.expense.model.Expense;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExpenseRepository extends JpaRepository<Expense, Long> {
}
