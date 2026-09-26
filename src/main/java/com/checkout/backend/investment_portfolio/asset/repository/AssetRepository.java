package com.checkout.backend.investment_portfolio.asset.repository;

import com.checkout.backend.investment_portfolio.asset.model.Asset;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AssetRepository extends JpaRepository<Asset, Long> {
    // Operable actives
    List<Asset> findByActiveTrueOrderBySymbolAsc();
    Optional<Asset> findBySymbolIgnoreCase(String symbol);
    boolean existsBySymbolIgnoreCase(String symbol);
}