package com.checkout.backend.investment_portfolio.asset.history.repository;

import com.checkout.backend.investment_portfolio.asset.history.model.AssetPriceHistory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HistoryRepository extends JpaRepository<AssetPriceHistory, Long> {
}
