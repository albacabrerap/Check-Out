package com.checkout.backend.investment_portfolio.repository;

import com.checkout.backend.investment_portfolio.model.InvestmentPortfolio;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InvestmentPortafolioRepository extends JpaRepository<InvestmentPortfolio, Long> {
}
