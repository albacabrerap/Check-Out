package com.checkout.backend.investment_portfolio.position.repository;

import com.checkout.backend.investment_portfolio.position.model.PortfolioPosition;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PortfolioPositionRepository extends JpaRepository<PortfolioPosition, Long> {
    // @EntityGraph addresses the N+1 problem.
    // The asset is included in the same join.

    @EntityGraph(attributePaths = "asset")
    List<PortfolioPosition> findByPortfolioId(Long portfolioId);
    Optional<PortfolioPosition> findByPortfolioIdAndAssetId(Long portfolioId, Long assetId);
}