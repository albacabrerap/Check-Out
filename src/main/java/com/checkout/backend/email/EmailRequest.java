package com.checkout.backend.email;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Lo que un cliente puede pedir que se envie.
 *
 * No incluye el destinatario a proposito, y esa ausencia es la correccion mas
 * importante de este modulo: el correo va siempre al usuario autenticado, que
 * sale del token. Si el destinatario viniera en el cuerpo, cualquier cuenta
 * podria mandar correo a cualquier direccion usando las credenciales del
 * proyecto, que es la definicion de un relay abierto y termina con la cuenta
 * de envio bloqueada.
 *
 * Tampoco incluye la ruta de un adjunto. La version anterior recibia un
 * String con una ruta del sistema de archivos y la adjuntaba tal cual, de modo
 * que se podia pedir el envio de cualquier fichero del servidor. Cuando hace
 * falta adjuntar algo, el archivo se sube en la peticion.
 *
 * @param subject asunto
 * @param body    cuerpo en texto plano
 */
public record EmailRequest(
        @NotBlank
        @Size(max = 200)
        String subject,

        @NotBlank
        @Size(max = 5000)
        String body
) {
}
