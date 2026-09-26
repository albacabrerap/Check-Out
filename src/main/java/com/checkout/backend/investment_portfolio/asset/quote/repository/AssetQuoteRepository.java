package com.checkout.backend.investment_portfolio.asset.quote.repository;

import com.checkout.backend.investment_portfolio.asset.quote.model.AssetQuote;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

// Access to the current price of each active.
public interface AssetQuoteRepository extends JpaRepository<AssetQuote, Long> {
    Optional<AssetQuote> findByAssetId(Long assetId);
    List<AssetQuote> findByAssetIdIn(Collection<Long> assetIds);
}