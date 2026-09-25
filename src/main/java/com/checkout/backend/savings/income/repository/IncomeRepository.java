package com.checkout.backend.savings.income.repository;

import com.checkout.backend.savings.income.model.Income;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Acceso a la tabla incomes.
 *
 * El orden y el filtro por rango se apoyan en idx_incomes_user_date, que es un
 * indice compuesto por (user_id, date).
 */
public interface IncomeRepository extends JpaRepository<Income, Long> {

    Page<Income> findByUserIdOrderByDateDesc(Long userId, Pageable pageable);

    Page<Income> findByUserIdAndDateBetweenOrderByDateDesc(
            Long userId, LocalDate from, LocalDate to, Pageable pageable);

    Optional<Income> findByIdAndUserId(Long id, Long userId);

}
