package com.checkout.backend.investment_portfolio.asset.quote.repository;

import com.checkout.backend.investment_portfolio.asset.quote.model.AssetQuote;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuoteRepository extends JpaRepository<AssetQuote, Long> {
}
