package com.checkout.backend.savings.income.repository;

import com.checkout.backend.savings.income.model.Income;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IncomeRepository extends JpaRepository<Income, Long> {
}
