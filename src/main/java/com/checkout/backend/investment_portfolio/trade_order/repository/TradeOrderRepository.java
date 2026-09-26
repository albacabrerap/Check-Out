package com.checkout.backend.investment_portfolio.trade_order.repository;

import com.checkout.backend.investment_portfolio.trade_order.model.TradeOrder;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TradeOrderRepository extends JpaRepository<TradeOrder, Long> {
    Page<TradeOrder> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);
    Optional<TradeOrder> findByIdAndUserId(Long id, Long userId);
    Optional<TradeOrder> findByClientOrderIdAndUserId(UUID clientOrderId, Long userId);
}