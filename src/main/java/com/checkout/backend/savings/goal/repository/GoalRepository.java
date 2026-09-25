package com.checkout.backend.savings.goal.repository;

import com.checkout.backend.savings.goal.model.GoalStatus;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GoalRepository extends JpaRepository<GoalStatus, Long> {
}
