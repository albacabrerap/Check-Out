package com.checkout.backend.email;

import java.util.concurrent.CompletableFuture;
import org.springframework.core.io.Resource;

/**
 * Envio de correo, siempre fuera del hilo de la peticion.
 *
 * Es interfaz y no clase concreta porque aqui si hay algo que variar: un test
 * necesita comprobar que se pidio el envio sin levantar un servidor SMTP, y un
 * doble de esta interfaz es la forma limpia de conseguirlo.
 *
 * Devuelve CompletableFuture y no void para que quien llama pueda enterarse del
 * resultado con whenComplete. Con void, un fallo de SMTP solo existiria en el
 * log del hilo de correo y nadie mas podria reaccionar.
 */
public interface EmailService {

    CompletableFuture<Void> send(EmailDetails details);

    /**
     * Envia con un adjunto ya cargado.
     *
     * Recibe un Resource y no una ruta: asi el llamante decide de donde sale el
     * contenido — una subida del usuario, algo generado en memoria — y este
     * servicio nunca toca el sistema de archivos con un dato que venga de fuera.
     *
     * @param filename nombre con el que el adjunto llega al buzon
     */
    CompletableFuture<Void> sendWithAttachment(EmailDetails details, Resource attachment,
                                               String filename);
}
