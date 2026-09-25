package com.checkout.backend.investment_portfolio.trade_order.event;

import com.checkout.backend.investment_portfolio.trade_order.model.OrderSide;
import java.math.BigDecimal;

/**
 * Una orden de compra o venta se ejecuto contra la cotizacion vigente.
 *
 * Se publica solo para las ejecutadas, no para las rechazadas: un rechazo ya
 * queda en el historial con su motivo y no es un hecho que otro modulo necesite
 * saber. Un movimiento real de cartera si lo es.
 *
 * @param orderId        orden ejecutada
 * @param userId         dueno de la orden
 * @param userEmail      destinatario de la notificacion, resuelto en el servidor
 * @param symbol         simbolo del activo operado
 * @param side           compra o venta
 * @param quantity       cantidad operada
 * @param executionPrice precio al que se ejecuto
 * @param tokensMoved    fichas que cambiaron de manos
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
) {
}
