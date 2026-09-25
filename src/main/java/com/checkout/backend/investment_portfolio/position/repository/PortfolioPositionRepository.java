package com.checkout.backend.investment_portfolio.position.repository;

import com.checkout.backend.investment_portfolio.position.model.PortfolioPosition;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

/** Acceso a las posiciones de una cartera. */
public interface PortfolioPositionRepository extends JpaRepository<PortfolioPosition, Long> {

    /**
     * Las posiciones de una cartera, con su activo ya cargado.
     *
     * El @EntityGraph esta por el N+1: valorar una posicion necesita el simbolo
     * del activo, y con el @ManyToOne LAZY cada elemento del listado disparaba su
     * propia consulta. Con veinte posiciones eran veintiuna consultas para pintar
     * una pantalla. Aqui el activo viaja en el mismo join.
     */
    @EntityGraph(attributePaths = "asset")
    List<PortfolioPosition> findByPortfolioId(Long portfolioId);

    Optional<PortfolioPosition> findByPortfolioIdAndAssetId(Long portfolioId, Long assetId);
}
