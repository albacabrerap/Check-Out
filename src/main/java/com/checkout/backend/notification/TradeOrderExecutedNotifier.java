package com.checkout.backend.notification;

import com.checkout.backend.email.EmailDetails;
import com.checkout.backend.email.EmailService;
import com.checkout.backend.investment_portfolio.trade_order.event.TradeOrderExecutedEvent;
import com.checkout.backend.investment_portfolio.trade_order.model.OrderSide;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Manda el comprobante de una orden ejecutada.
 *
 * Misma razon que en GoalCompletedNotifier para el AFTER_COMMIT, y aqui pesa
 * todavia mas: el comprobante de una operacion que no llego a guardarse seria un
 * documento sobre algo que no paso. Y el fallo de correo tampoco puede tumbar la
 * operacion, porque las fichas y la posicion ya cambiaron de manos.
 */
@Component
public class TradeOrderExecutedNotifier {

    private static final Logger log = LoggerFactory.getLogger(TradeOrderExecutedNotifier.class);

    private final EmailService emailService;

    public TradeOrderExecutedNotifier(EmailService emailService) {
        this.emailService = emailService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderExecuted(TradeOrderExecutedEvent event) {
        String verb = event.side() == OrderSide.BUY ? "compra" : "venta";

        String body = """
                Tu orden de %s se ejecuto.

                Activo:   %s
                Cantidad: %s
                Precio:   %s
                Fichas:   %s

                Puedes ver el detalle en tu historial de ordenes.
                """.formatted(verb, event.symbol(), event.quantity(),
                event.executionPrice(), event.tokensMoved());

        try {
            emailService.send(new EmailDetails(
                            event.userEmail(),
                            "Orden de " + verb + " ejecutada: " + event.symbol(),
                            body))
                    .whenComplete((ignored, failure) -> {
                        if (failure != null) {
                            log.error("No se pudo enviar el comprobante de la orden {}",
                                    event.orderId(), failure);
                        }
                    });
        } catch (RuntimeException ex) {
            log.error("Fallo al encolar el comprobante de la orden {}", event.orderId(), ex);
        }
    }
}
