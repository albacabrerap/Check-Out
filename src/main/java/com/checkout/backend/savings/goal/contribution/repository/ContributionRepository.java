package com.checkout.backend.savings.goal.contribution.repository;

import com.checkout.backend.savings.goal.contribution.dto.ContributionRequest;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContributionRepository extends JpaRepository<ContributionRequest, Long> {
}
