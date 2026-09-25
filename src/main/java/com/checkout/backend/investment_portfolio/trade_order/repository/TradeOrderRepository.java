package com.checkout.backend.investment_portfolio.trade_order.repository;

import com.checkout.backend.investment_portfolio.trade_order.model.TradeOrder;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Acceso al libro de ordenes.
 */
public interface TradeOrderRepository extends JpaRepository<TradeOrder, Long> {

    Page<TradeOrder> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    Optional<TradeOrder> findByIdAndUserId(Long id, Long userId);

    /**
     * Busca por el identificador que genera el cliente, que tiene UNIQUE en la
     * tabla y sirve de clave de idempotencia.
     *
     * Sin esto, un cliente que reintenta porque no le llego la respuesta acabaria
     * comprando dos veces. Con esto, el reintento devuelve la orden que ya se
     * ejecuto. Va filtrado ademas por usuario para que nadie pueda sondear
     * identificadores ajenos.
     */
    Optional<TradeOrder> findByClientOrderIdAndUserId(UUID clientOrderId, Long userId);
}
