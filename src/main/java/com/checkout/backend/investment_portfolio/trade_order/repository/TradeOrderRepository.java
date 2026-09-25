package com.checkout.backend.investment_portfolio.trade_order.repository;

import com.checkout.backend.investment_portfolio.trade_order.model.TradeOrder;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TradeOrderRepository extends JpaRepository<TradeOrder, Long> {
}
