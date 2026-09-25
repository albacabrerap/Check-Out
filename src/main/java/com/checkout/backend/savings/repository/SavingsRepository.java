package com.checkout.backend.savings.repository;

import com.checkout.backend.savings.model.Savings;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SavingsRepository extends JpaRepository<Savings, Long> {
}