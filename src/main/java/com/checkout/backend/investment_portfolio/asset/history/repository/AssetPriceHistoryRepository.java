package com.checkout.backend.investment_portfolio.asset.history.repository;

import com.checkout.backend.investment_portfolio.asset.history.model.AssetPriceHistory;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

// Access to closing a day for each active.
public interface AssetPriceHistoryRepository extends JpaRepository<AssetPriceHistory, Long> {
    List<AssetPriceHistory> findByAssetIdAndDateBetweenOrderByDateAsc(
            Long assetId, LocalDate from, LocalDate to);
    List<AssetPriceHistory> findByAssetIdOrderByDateAsc(Long assetId);
}