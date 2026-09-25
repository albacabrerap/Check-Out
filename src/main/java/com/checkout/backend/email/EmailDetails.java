package com.checkout.backend.email;

/**
 * Un correo listo para enviar.
 *
 * Es un objeto interno, no un DTO de entrada: lo construye el servicio o un
 * listener de eventos, nunca llega desde una peticion. Por eso lleva el
 * destinatario, que es justamente el campo que ningun cliente debe poder elegir.
 *
 * Es un record porque un correo se arma una vez y se manda; no hay nada que
 * modificar despues.
 *
 * @param recipient direccion de destino, resuelta en el servidor
 * @param subject   asunto
 * @param body      cuerpo en texto plano
 */
public record EmailDetails(String recipient, String subject, String body) {
}
