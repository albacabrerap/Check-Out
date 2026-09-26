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

// SMTP send to email thread.
        /*
JavaMailSender autoconfigures Spring Boot with spring.mail.
    to avoid duplications of the host and port in the code and application.properties.
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

    // @Async takes it out of the petition thread
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

    // Without a configured remitent, no possible send.
    private void requireSenderConfigured() {
        if (sender == null || sender.isBlank()) {
            throw new EmailSenderException(
                    "El remitente no esta configurado: falta la variable MAIL_USERNAME",
                    new IllegalStateException("spring.mail.username vacio"));
        }
    }
}