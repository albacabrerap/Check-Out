package com.checkout.backend.investment_portfolio.trade_order.event;

import com.checkout.backend.investment_portfolio.trade_order.model.OrderSide;
import java.math.BigDecimal;

// A buy or sell order was executed against the current quote.
    /*
        orderId: executed request.
        userId: order request.
        userEmail: notification addressee.
        symbol: active symbol
        side: buy or sell
        quantity: operated quantity.
        executionPrice: price of execution.
        tokensMoved: tokens changed.
    */

public record TradeOrderExecutedEvent(
        Long orderId,
        Long userId,
        String userEmail,
        String symbol,
        OrderSide side,
        BigDecimal quantity,
        BigDecimal executionPrice,
        BigDecimal tokensMoved
) { }