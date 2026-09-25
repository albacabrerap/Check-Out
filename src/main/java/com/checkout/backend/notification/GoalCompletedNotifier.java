package com.checkout.backend.notification;

import com.checkout.backend.email.EmailDetails;
import com.checkout.backend.email.EmailService;
import com.checkout.backend.savings.goal.event.SavingsGoalCompletedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Avisa por correo cuando una meta de ahorro se cumple.
 *
 * Es @TransactionalEventListener con phase AFTER_COMMIT, y esa eleccion es todo
 * el contenido de esta clase. Un @EventListener normal se ejecuta dentro de la
 * transaccion que publico el evento, y eso trae dos problemas que no se ven hasta
 * que pasan:
 *
 * <ol>
 *   <li>El correo saldria antes del commit. Si la transaccion acabara en
 *       rollback —un conflicto de bloqueo optimista con otro aporte simultaneo
 *       basta— el usuario tendria en su buzon la felicitacion por una meta que la
 *       base nunca registro como cumplida. Un correo no se puede deshacer con la
 *       transaccion.</li>
 *   <li>El envio ocurriria dentro del tiempo de la peticion y con la transaccion
 *       abierta, dejando una conexion de base ocupada mientras se espera a un
 *       servidor SMTP.</li>
 * </ol>
 *
 * Con AFTER_COMMIT el listener solo corre si el commit ocurrio de verdad, y el
 * envio en si es @Async dentro de EmailService, asi que tampoco bloquea.
 *
 * Este listener no lanza: un fallo de correo no debe convertirse en un error de
 * la operacion que ya se completo y se guardo. Se registra y ahi se queda, que es
 * exactamente la diferencia entre una notificacion y un paso del negocio.
 */
@Component
public class GoalCompletedNotifier {

    private static final Logger log = LoggerFactory.getLogger(GoalCompletedNotifier.class);

    private final EmailService emailService;

    public GoalCompletedNotifier(EmailService emailService) {
        this.emailService = emailService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onGoalCompleted(SavingsGoalCompletedEvent event) {
        String body = """
                Felicidades: cumpliste tu meta "%s".

                Objetivo alcanzado: S/ %s
                Fichas acreditadas: %s

                Puedes usar tus fichas en los minijuegos y en la cartera simulada.
                """.formatted(event.goalName(), event.targetAmount(), event.tokensAwarded());

        try {
            emailService.send(new EmailDetails(
                            event.userEmail(),
                            "Cumpliste tu meta: " + event.goalName(),
                            body))
                    .whenComplete((ignored, failure) -> {
                        if (failure != null) {
                            log.error("No se pudo avisar de la meta {} cumplida por el usuario {}",
                                    event.goalId(), event.userId(), failure);
                        }
                    });
        } catch (RuntimeException ex) {
            // La meta ya esta cumplida y guardada. Que no salga el correo es un
            // problema, pero no es motivo para que la peticion falle.
            log.error("Fallo al encolar el aviso de la meta {}", event.goalId(), ex);
        }
    }
}
