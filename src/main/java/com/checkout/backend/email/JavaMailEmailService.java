package com.checkout.backend.email;

import com.checkout.backend.exceptions.EmailSenderException;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Envio real por SMTP, en el pool de hilos de correo.
 *
 * El nombre dice con que se implementa, que es lo que distingue a esta clase de
 * cualquier otra implementacion futura. "Implemented" no distingue nada: si
 * manana entra un envio por API de un proveedor, las dos clases se llamarian
 * igual de bien.
 *
 * El JavaMailSender lo autoconfigura Spring Boot a partir de spring.mail.*, asi
 * que no hace falta construirlo a mano: hacerlo duplicaba el host y el puerto
 * en el codigo y en application.properties, y dos sitios para el mismo dato
 * acaban divergiendo.
 */
@Service
public class JavaMailEmailService implements EmailService {

    private static final Logger log = LoggerFactory.getLogger(JavaMailEmailService.class);

    private final JavaMailSender mailSender;
    private final String sender;

    public JavaMailEmailService(JavaMailSender mailSender,
                                @Value("${spring.mail.username:}") String sender) {
        this.mailSender = mailSender;
        this.sender = sender;
    }

    /**
     * @Async lo saca del hilo de la peticion: un SMTP lento no debe dejar
     * esperando a quien pidio la operacion. El nombre del executor es explicito
     * para que el correo use su propio pool y no compita con el resto de tareas
     * asincronas que el proyecto tenga despues.
     */
    @Async("mailExecutor")
    @Override
    public CompletableFuture<Void> send(EmailDetails details) {
        requireSenderConfigured();
        log.debug("Enviando correo a {} en el hilo {}",
                details.recipient(), Thread.currentThread().getName());

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(sender);
            message.setTo(details.recipient());
            message.setSubject(details.subject());
            message.setText(details.body());

            mailSender.send(message);
            return CompletableFuture.completedFuture(null);

        } catch (MailException ex) {
            // El mensaje de la excepcion puede traer el servidor y la cuenta, asi
            // que se envuelve: el handler global responde 502 con un texto fijo y
            // el detalle se queda en el log.
            throw new EmailSenderException("Fallo el envio a " + details.recipient(), ex);
        }
    }

    @Async("mailExecutor")
    @Override
    public CompletableFuture<Void> sendWithAttachment(EmailDetails details, Resource attachment,
                                                      String filename) {
        requireSenderConfigured();
        log.debug("Enviando correo con adjunto a {} en el hilo {}",
                details.recipient(), Thread.currentThread().getName());

        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            // true: el mensaje es multipart, que es lo que permite el adjunto.
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true);

            helper.setFrom(sender);
            helper.setTo(details.recipient());
            helper.setSubject(details.subject());
            helper.setText(details.body());
            helper.addAttachment(filename, attachment);

            mailSender.send(mimeMessage);
            return CompletableFuture.completedFuture(null);

        } catch (MessagingException | MailException ex) {
            throw new EmailSenderException("Fallo el envio con adjunto a " + details.recipient(), ex);
        }
    }

    /**
     * Sin remitente configurado no hay envio posible.
     *
     * MAIL_USERNAME tiene default vacio para que la aplicacion arranque sin
     * configuracion de correo — el resto de la API no deberia depender de ella —
     * pero entonces el fallo tiene que ser claro aqui y no un error de SMTP que
     * nadie sepa interpretar.
     */
    private void requireSenderConfigured() {
        if (sender == null || sender.isBlank()) {
            throw new EmailSenderException(
                    "El remitente no esta configurado: falta la variable MAIL_USERNAME",
                    new IllegalStateException("spring.mail.username vacio"));
        }
    }

}
