package com.checkout.backend.savings.goal.event;

import java.math.BigDecimal;

/**
 * Una meta de ahorro alcanzo su objetivo.
 *
 * Es un evento de dominio y no una llamada directa al servicio de correo, y la
 * diferencia importa: quien registra un aporte no tiene por que saber que
 * completar una meta manda un correo. Si manana hay que mandar tambien una
 * notificacion push o apuntarlo en un informe, se suscribe otro listener y
 * ContributionService no se toca.
 *
 * Lleva todo lo que un suscriptor necesita, incluido el correo del usuario, en
 * vez de solo los ids. La razon es que se consume despues del commit y a veces
 * en otro hilo: volver a la base desde ahi para resolver el usuario es una
 * consulta extra que puede encontrarse la entidad ya desasociada de la sesion.
 * Un evento que se explica solo no tiene ese problema.
 *
 * @param goalId         meta que se completo
 * @param userId         dueno de la meta
 * @param userEmail      destinatario de la notificacion, resuelto en el servidor
 * @param goalName       nombre que el usuario le puso a la meta
 * @param targetAmount   objetivo alcanzado
 * @param tokensAwarded  fichas acreditadas por cumplirla
 */
public record SavingsGoalCompletedEvent(
        Long goalId,
        Long userId,
        String userEmail,
        String goalName,
        BigDecimal targetAmount,
        BigDecimal tokensAwarded
) {
}
