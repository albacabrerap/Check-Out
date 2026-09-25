package com.checkout.backend.savings.expense.repository;

import com.checkout.backend.savings.expense.model.Expense;
import com.checkout.backend.savings.expense.model.ExpenseCategory;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Acceso a la tabla expenses.
 *
 * Igual que en incomes, el orden por fecha descendente aprovecha
 * idx_expenses_user_date.
 */
public interface ExpenseRepository extends JpaRepository<Expense, Long> {

    Page<Expense> findByUserIdOrderByDateDesc(Long userId, Pageable pageable);

    Page<Expense> findByUserIdAndDateBetweenOrderByDateDesc(
            Long userId, LocalDate from, LocalDate to, Pageable pageable);

    Page<Expense> findByUserIdAndCategoryOrderByDateDesc(
            Long userId, ExpenseCategory category, Pageable pageable);

    Optional<Expense> findByIdAndUserId(Long id, Long userId);

}
