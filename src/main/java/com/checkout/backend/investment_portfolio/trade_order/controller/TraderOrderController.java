package com.checkout.backend.investment_portfolio.trade_order.controller;

import com.checkout.backend.investment_portfolio.trade_order.repository.TradeOrderRepository;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/trade_order")
public class TraderOrderController {
    private final TradeOrderRepository tradeOrderRepository;

    public TraderOrderController(TradeOrderRepository tradeOrderRepository) {
        this.tradeOrderRepository = tradeOrderRepository;
    }

    // ...
}
