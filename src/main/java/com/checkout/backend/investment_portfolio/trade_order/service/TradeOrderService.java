package com.checkout.backend.investment_portfolio.trade_order.service;

import com.checkout.backend.exceptions.InvalidRequestException;
import com.checkout.backend.exceptions.ResourceNotFoundException;
import com.checkout.backend.investment_portfolio.asset.model.Asset;
import com.checkout.backend.investment_portfolio.asset.service.AssetService;
import com.checkout.backend.investment_portfolio.service.PortfolioService;
import com.checkout.backend.investment_portfolio.trade_order.dto.TradeOrderRequest;
import com.checkout.backend.investment_portfolio.trade_order.event.TradeOrderExecutedEvent;
import com.checkout.backend.investment_portfolio.trade_order.dto.TradeOrderResponse;
import com.checkout.backend.investment_portfolio.trade_order.model.OrderSide;
import com.checkout.backend.investment_portfolio.trade_order.model.OrderStatus;
import com.checkout.backend.investment_portfolio.trade_order.model.TradeOrder;
import com.checkout.backend.investment_portfolio.trade_order.repository.TradeOrderRepository;
import com.checkout.backend.token_wallet.service.TokenWalletService;
import com.checkout.backend.token_wallet.tktransaction.model.TokenReason;
import com.checkout.backend.user.model.User;
import com.checkout.backend.web.PageResponse;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.modelmapper.ModelMapper;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TradeOrderService {
    private static final BigDecimal TOKEN_RATE = BigDecimal.ONE;
    private final TradeOrderRepository orderRepository;
    private final AssetService assetService;
    private final PortfolioService portfolioService;
    private final TokenWalletService walletService;
    private final ApplicationEventPublisher events;
    private final ModelMapper mapper;

    public TradeOrderService(TradeOrderRepository orderRepository,
                             AssetService assetService,
                             PortfolioService portfolioService,
                             TokenWalletService walletService,
                             ApplicationEventPublisher events,
                             ModelMapper mapper) {
        this.orderRepository = orderRepository;
        this.assetService = assetService;
        this.portfolioService = portfolioService;
        this.walletService = walletService;
        this.events = events;
        this.mapper = mapper;
    }

    @Transactional
    public TradeOrderResponse place(User user, TradeOrderRequest request) {
        var alreadyPlaced = orderRepository
                .findByClientOrderIdAndUserId(request.getClientOrderId(), user.getId());
        if (alreadyPlaced.isPresent()) {
            return toResponse(alreadyPlaced.get());
        }

        Asset asset = assetService.findTradable(request.getAssetId());

        TradeOrder order = TradeOrder.builder()
                .user(user)
                .asset(asset)
                .clientOrderId(request.getClientOrderId())
                .side(request.getSide())
                .quantity(request.getQuantity())
                .status(OrderStatus.PENDING)
                .build();

        BigDecimal price = assetService.requireQuote(asset).getPrice();
        BigDecimal tokens = request.getQuantity().multiply(price).multiply(TOKEN_RATE)
                .setScale(2, RoundingMode.HALF_UP);

        order.setExecutionPrice(price);
        order.setTokenRate(TOKEN_RATE);
        order.setTokensMoved(tokens);

        Optional<String> rejection = request.getSide() == OrderSide.BUY
                ? walletService.reasonToRejectSpending(user, tokens)
                : portfolioService.reasonToRejectSell(user, asset, request.getQuantity());

        if (rejection.isPresent()) {
            order.setStatus(OrderStatus.REJECTED);
            order.setRejectionReason(rejection.get());
            order.setTokensMoved(BigDecimal.ZERO);
            return toResponse(orderRepository.save(order));
        }

        TradeOrder saved = orderRepository.save(order);

        if (request.getSide() == OrderSide.BUY) {
            walletService.record(user, tokens.negate(), TokenReason.INVESTMENT, saved.getId());
            portfolioService.applyBuy(user, asset, request.getQuantity(), price, tokens);
        } else {
            portfolioService.applySell(user, asset, request.getQuantity(), price);
            walletService.record(user, tokens, TokenReason.INVESTMENT, saved.getId());
        }

        saved.setStatus(OrderStatus.EXECUTED);
        saved.setExecutedAt(LocalDateTime.now());

        events.publishEvent(new TradeOrderExecutedEvent(
                saved.getId(), user.getId(), user.getEmail(), asset.getSymbol(),
                saved.getSide(), saved.getQuantity(), price, tokens));

        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public PageResponse<TradeOrderResponse> list(User user, Pageable pageable) {
        return PageResponse.of(orderRepository
                .findByUserIdOrderByCreatedAtDesc(user.getId(), pageable)
                .map(this::toResponse));
    }

    @Transactional(readOnly = true)
    public TradeOrderResponse get(User user, Long id) {
        return toResponse(findOwned(user, id));
    }

    @Transactional
    public TradeOrderResponse cancel(User user, Long id) {
        TradeOrder order = findOwned(user, id);

        if (order.getStatus() != OrderStatus.PENDING) {
            throw new InvalidRequestException(
                    "Solo se puede cancelar una orden pendiente. Esta esta " + order.getStatus() + ".");
        }

        order.setStatus(OrderStatus.CANCELLED);
        return toResponse(order);
    }

    private TradeOrder findOwned(User user, Long id) {
        return orderRepository.findByIdAndUserId(id, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Orden", id));
    }

    private TradeOrderResponse toResponse(TradeOrder order) {
        return mapper.map(order, TradeOrderResponse.class);
    }
}