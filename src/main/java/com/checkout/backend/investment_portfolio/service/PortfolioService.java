package com.checkout.backend.investment_portfolio.service;

import com.checkout.backend.exceptions.InvalidRequestException;
import com.checkout.backend.investment_portfolio.asset.model.Asset;
import com.checkout.backend.investment_portfolio.asset.service.AssetService;
import com.checkout.backend.investment_portfolio.dto.PortfolioResponse;
import com.checkout.backend.investment_portfolio.model.InvestmentPortfolio;
import com.checkout.backend.investment_portfolio.position.dto.PositionResponse;
import com.checkout.backend.investment_portfolio.position.model.PortfolioPosition;
import com.checkout.backend.investment_portfolio.position.repository.PortfolioPositionRepository;
import com.checkout.backend.investment_portfolio.repository.InvestmentPortfolioRepository;
import com.checkout.backend.user.model.User;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.modelmapper.ModelMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Market value is calculated using the current price quote at the time of reading.

@Service
public class PortfolioService {

    private static final Logger log = LoggerFactory.getLogger(PortfolioService.class);

    private final InvestmentPortfolioRepository portfolioRepository;
    private final PortfolioPositionRepository positionRepository;
    private final AssetService assetService;
    private final ModelMapper mapper;

    public PortfolioService(InvestmentPortfolioRepository portfolioRepository,
                            PortfolioPositionRepository positionRepository,
                            AssetService assetService,
                            ModelMapper mapper) {
        this.portfolioRepository = portfolioRepository;
        this.positionRepository = positionRepository;
        this.assetService = assetService;
        this.mapper = mapper;
    }

    @Transactional
    public InvestmentPortfolio getOrCreate(User user) {
        return portfolioRepository.findByUserId(user.getId())
                .orElseGet(() -> portfolioRepository.save(
                        InvestmentPortfolio.builder()
                                .user(user)
                                .investedTokens(BigDecimal.ZERO)
                                .simulatedValue(BigDecimal.ZERO)
                                .build()));
    }

    // Portfolio with valued positions.
    // simulatedValue it's recalculated and saved on each step...

    @Transactional
    public PortfolioResponse getSummary(User user) {
        InvestmentPortfolio portfolio = getOrCreate(user);

        List<PositionResponse> positions =
                valuePositions(positionRepository.findByPortfolioId(portfolio.getId()));

        BigDecimal marketValue = positions.stream()
                .map(PositionResponse::getMarketValue)
                .filter(value -> value != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        portfolio.setSimulatedValue(marketValue);

        PortfolioResponse response = mapper.map(portfolio, PortfolioResponse.class);
        response.setPositions(positions);
        response.setUnrealizedPnl(marketValue.subtract(portfolio.getInvestedTokens()));
        return response;
    }

    @Transactional
    public List<PositionResponse> listPositions(User user) {
        return valuePositions(positionRepository.findByPortfolioId(getOrCreate(user).getId()));
    }

    // Evaluates a list of positions using a total of two queries.
    // The asset is already loaded via the repository's @EntityGraph.
    // The quotes are fetched all at once and indexed by asset.

    private List<PositionResponse> valuePositions(List<PortfolioPosition> positions) {
        if (positions.isEmpty()) {
            return List.of();
        }

        Map<Long, BigDecimal> pricesByAsset = assetService
                .currentPrices(positions.stream()
                        .map(position -> position.getAsset().getId())
                        .distinct()
                        .toList());

        return positions.stream()
                .map(position -> toPositionResponse(
                        position, pricesByAsset.get(position.getAsset().getId())))
                .toList();
    }

    // Adds a purchase to the asset position or creates it.
    // The average cost is recalculated by weighting the existing stock against the incoming stock.

    @Transactional
    public void applyBuy(User user, Asset asset, BigDecimal quantity, BigDecimal price,
                         BigDecimal tokensSpent) {
        InvestmentPortfolio portfolio = getOrCreate(user);

        PortfolioPosition position = positionRepository
                .findByPortfolioIdAndAssetId(portfolio.getId(), asset.getId())
                .orElse(null);

        if (position == null) {
            position = PortfolioPosition.builder()
                    .portfolio(portfolio)
                    .asset(asset)
                    .quantity(quantity)
                    .averageCost(price)
                    .build();
            portfolio.addPosition(position);
            positionRepository.save(position);
        } else {
            BigDecimal previousCost = position.getQuantity().multiply(position.getAverageCost());
            BigDecimal addedCost = quantity.multiply(price);
            BigDecimal newQuantity = position.getQuantity().add(quantity);

            position.setAverageCost(
                    previousCost.add(addedCost).divide(newQuantity, 4, RoundingMode.HALF_UP));
            position.setQuantity(newQuantity);
        }

        portfolio.setInvestedTokens(portfolio.getInvestedTokens().add(tokensSpent));
    }

    @Transactional(readOnly = true)
    public Optional<String> reasonToRejectSell(User user, Asset asset, BigDecimal quantity) {
        var portfolio = portfolioRepository.findByUserId(user.getId());
        if (portfolio.isEmpty()) {
            return Optional.of("No tienes ninguna posicion en " + asset.getSymbol() + ".");
        }

        var position = positionRepository
                .findByPortfolioIdAndAssetId(portfolio.get().getId(), asset.getId());
        if (position.isEmpty()) {
            return Optional.of("No tienes ninguna posicion en " + asset.getSymbol() + ".");
        }
        if (position.get().getQuantity().compareTo(quantity) < 0) {
            return Optional.of(
                    "Solo tienes " + position.get().getQuantity() + " de " + asset.getSymbol() + ".");
        }
        return Optional.empty();
    }

    // Deducts a sale from the position.

    @Transactional
    public void applySell(User user, Asset asset, BigDecimal quantity, BigDecimal price) {
        InvestmentPortfolio portfolio = getOrCreate(user);

        PortfolioPosition position = positionRepository
                .findByPortfolioIdAndAssetId(portfolio.getId(), asset.getId())
                .orElseThrow(() -> new InvalidRequestException(
                        "No tienes ninguna posicion en " + asset.getSymbol() + "."));

        if (position.getQuantity().compareTo(quantity) < 0) {
            throw new InvalidRequestException(
                    "Solo tienes " + position.getQuantity() + " de " + asset.getSymbol() + ".");
        }

        BigDecimal releasedCost = quantity.multiply(position.getAverageCost())
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal proceeds = quantity.multiply(price).setScale(2, RoundingMode.HALF_UP);
        BigDecimal realized = proceeds.subtract(releasedCost);

        BigDecimal remaining = position.getQuantity().subtract(quantity);
        if (remaining.signum() == 0) {
            portfolio.removePosition(position);
            positionRepository.delete(position);
        } else {
            position.setQuantity(remaining);
        }

        BigDecimal remainingInvested = portfolio.getInvestedTokens().subtract(releasedCost);

        // max(0) is a safety net.
        if (remainingInvested.signum() < 0) {
            log.warn("investedTokens quedaria en {} para la cartera {}: el acumulado se "
                            + "desvio del coste de las posiciones. Se ajusta a cero.",
                    remainingInvested, portfolio.getId());
        }

        portfolio.setInvestedTokens(remainingInvested.max(BigDecimal.ZERO));
        portfolio.setRealizedPnl(portfolio.getRealizedPnl().add(realized));
    }

    // Values a position using the provided price.
    private PositionResponse toPositionResponse(PortfolioPosition position, BigDecimal price) {
        PositionResponse response = mapper.map(position, PositionResponse.class);

        if (price != null) {
            BigDecimal marketValue = position.getQuantity().multiply(price)
                    .setScale(2, RoundingMode.HALF_UP);

            response.setCurrentPrice(price);
            response.setMarketValue(marketValue);
            response.setUnrealizedPnl(marketValue.subtract(
                    position.getQuantity().multiply(position.getAverageCost())
                            .setScale(2, RoundingMode.HALF_UP)));
        } return response;
    }
}