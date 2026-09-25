package com.checkout.backend.investment_portfolio.asset.repository;

import com.checkout.backend.investment_portfolio.asset.model.Asset;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AssetRepository extends JpaRepository<Asset, Long> {
}
