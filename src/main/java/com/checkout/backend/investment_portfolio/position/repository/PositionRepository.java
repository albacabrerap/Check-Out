package com.checkout.backend.investment_portfolio.position.repository;

import com.checkout.backend.investment_portfolio.position.model.PortfolioPosition;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PositionRepository extends JpaRepository<PortfolioPosition, Long> {
}
