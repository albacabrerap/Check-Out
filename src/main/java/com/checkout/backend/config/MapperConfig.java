package com.checkout.backend.config;

import com.checkout.backend.investment_portfolio.asset.quote.dto.AssetQuoteResponse;
import com.checkout.backend.investment_portfolio.asset.quote.model.AssetQuote;
import com.checkout.backend.investment_portfolio.position.dto.PositionResponse;
import com.checkout.backend.investment_portfolio.position.model.PortfolioPosition;
import com.checkout.backend.investment_portfolio.trade_order.dto.TradeOrderResponse;
import com.checkout.backend.investment_portfolio.trade_order.model.TradeOrder;
import com.checkout.backend.minigame.session.dto.MinigameSessionResponse;
import com.checkout.backend.minigame.session.model.MinigameSession;
import com.checkout.backend.projection.dto.ProjectionResponse;
import com.checkout.backend.projection.model.Projection;
import com.checkout.backend.savings.goal.model.SavingsGoal;
import org.modelmapper.ModelMapper;
import org.modelmapper.config.Configuration.AccessLevel;
import org.modelmapper.convention.MatchingStrategies;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// Single ModelMapper bean shared by every service.
// Note for callers: map inside the transaction or fetch the association first, otherwise Hibernate throws LazyInitializationException.

/*
 <ul>
   <li>SavingsResponse.committedAmount, availableBalance</li>
   <li>PortfolioResponse.unrealizedPnl</li>
   <li>PositionResponse.currentPrice, marketValue, unrealizedPnl</li>
   <li>ProjectionResponse.totalContributed, difference</li>
   <li>SavingsGoalResponse.progressPercent</li>
 </ul>
 */

@Configuration
public class MapperConfig {

    @Bean
    public ModelMapper modelMapper() {
        ModelMapper modelMapper = new ModelMapper();

        modelMapper.getConfiguration()
                .setMatchingStrategy(MatchingStrategies.STRICT)
                .setFieldMatchingEnabled(true)
                .setFieldAccessLevel(AccessLevel.PRIVATE)
                .setSkipNullEnabled(true);

        addFlattenedMappings(modelMapper);
        return modelMapper;
    }

    // Maps the response fields that come from a nested entity.
    private void addFlattenedMappings(ModelMapper modelMapper) {
        modelMapper.typeMap(AssetQuote.class, AssetQuoteResponse.class)
                .addMappings(map -> {
                    map.map(src -> src.getAsset().getSymbol(), AssetQuoteResponse::setSymbol);
                    map.map(src -> src.getAsset().getCurrency(), AssetQuoteResponse::setCurrency);
                });

        modelMapper.typeMap(PortfolioPosition.class, PositionResponse.class)
                .addMappings(map -> {
                    map.map(src -> src.getAsset().getSymbol(), PositionResponse::setSymbol);
                    map.map(src -> src.getAsset().getName(), PositionResponse::setAssetName);
                });

        modelMapper.typeMap(TradeOrder.class, TradeOrderResponse.class)
                .addMappings(map ->
                        map.map(src -> src.getAsset().getSymbol(), TradeOrderResponse::setSymbol));

        modelMapper.typeMap(MinigameSession.class, MinigameSessionResponse.class)
                .addMappings(map ->
                        map.map(src -> src.getMinigame().getTitle(),
                                MinigameSessionResponse::setMinigameTitle));

        // The goal is optional, so this one has to survive a null association.
        modelMapper.typeMap(Projection.class, ProjectionResponse.class)
                .addMappings(map -> map.using(ctx -> {
                    SavingsGoal goal = (SavingsGoal) ctx.getSource();
                    return goal == null ? null : goal.getId();
                }).map(Projection::getSavingsGoal, ProjectionResponse::setSavingsGoalId));
    }
}