package com.checkout.backend.email;

import com.checkout.backend.config.AsyncFailureHandler;
import com.checkout.backend.user.model.User;
import com.checkout.backend.user.service.CurrentUserProvider;
import jakarta.validation.Valid;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Envio de correo al usuario autenticado.
 *
 * Dos reglas gobiernan este controller, y las dos existen por lo mismo: un
 * endpoint de correo es un arma si el cliente decide a quien y que se manda.
 *
 * El destinatario sale del token, nunca del cuerpo. Un endpoint que acepte la
 * direccion de destino permite a cualquier cuenta enviar correo arbitrario con
 * las credenciales del proyecto, y el proveedor acaba bloqueando la cuenta.
 *
 * El adjunto se sube en la peticion, nunca se nombra por ruta. Aceptar una ruta
 * del servidor deja que alguien pida el envio de .env o de cualquier otro
 * fichero a su propio buzon.
 *
 * La ruta nombra el recurso, /emails, y no la accion: el verbo ya lo dice el
 * metodo HTTP. El prefijo /api/v1 lo pone ApiVersioningConfig.
 */
@RestController
@RequestMapping("/emails")
public class EmailController {

    private static final Logger log = LoggerFactory.getLogger(EmailController.class);

    /** Tope del adjunto. Un correo con mas que esto lo rechazan igual casi todos los buzones. */
    private static final long MAX_ATTACHMENT_BYTES = 5L * 1024 * 1024;

    private final EmailService emailService;
    private final AsyncFailureHandler failureHandler;
    private final CurrentUserProvider currentUser;

    public EmailController(EmailService emailService,
                           AsyncFailureHandler failureHandler,
                           CurrentUserProvider currentUser) {
        this.emailService = emailService;
        this.failureHandler = failureHandler;
        this.currentUser = currentUser;
    }

    /**
     * POST /api/v1/emails
     *
     * Responde 202 y no 200: el correo queda encolado y se envia en otro hilo,
     * asi que cuando el cliente recibe la respuesta todavia no se ha enviado
     * nada. 200 afirmaria algo que no es cierto.
     *
     * Un fallo posterior no puede viajar en esta respuesta, que ya se fue; por
     * eso se engancha un whenComplete que lo registra.
     */
    @PostMapping
    public ResponseEntity<Void> send(@Valid @RequestBody EmailRequest request) {
        User user = currentUser.requireCurrentUser();

        emailService.send(new EmailDetails(user.getEmail(), request.subject(), request.body()))
                .whenComplete((ignored, ex) -> {
                    if (ex != null) {
                        failureHandler.handle("envio de correo", user.getEmail(), ex);
                    }
                });

        log.debug("Correo encolado para {}", user.getEmail());
        return ResponseEntity.accepted().build();
    }

    /**
     * POST /api/v1/emails/with-attachment
     *
     * El archivo viaja en la peticion como multipart. Se lee a memoria antes de
     * encolar porque el MultipartFile vive atado a la peticion: cuando el hilo
     * de correo lo abriera, el archivo temporal ya no estaria.
     */
    @PostMapping(path = "/with-attachment", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Void> sendWithAttachment(@Valid @RequestPart("email") EmailRequest request,
                                                   @RequestPart("file") MultipartFile file)
            throws IOException {

        if (file.isEmpty()) {
            throw new com.checkout.backend.exceptions.InvalidRequestException(
                    "El adjunto esta vacio.");
        }
        if (file.getSize() > MAX_ATTACHMENT_BYTES) {
            throw new com.checkout.backend.exceptions.InvalidRequestException(
                    "El adjunto supera el maximo de 5 MB.");
        }

        User user = currentUser.requireCurrentUser();
        ByteArrayResource content = new ByteArrayResource(file.getBytes());
        String filename = sanitize(file.getOriginalFilename());

        emailService.sendWithAttachment(
                        new EmailDetails(user.getEmail(), request.subject(), request.body()),
                        content, filename)
                .whenComplete((ignored, ex) -> {
                    if (ex != null) {
                        failureHandler.handle("envio con adjunto", user.getEmail(), ex);
                    }
                });

        return ResponseEntity.accepted().build();
    }

    /**
     * Se queda con el nombre del archivo y descarta cualquier ruta.
     *
     * El nombre lo elige el cliente, y uno como "../../algo" no puede escribir
     * nada porque el adjunto ya esta en memoria, pero si acabaria en la cabecera
     * del correo y en el nombre con el que el destinatario lo guarda. Quedarse
     * con el ultimo tramo evita esa rareza.
     */
    private static String sanitize(String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()) {
            return "adjunto";
        }
        String name = originalFilename.replace('\\', '/');
        int lastSlash = name.lastIndexOf('/');
        String trimmed = lastSlash >= 0 ? name.substring(lastSlash + 1) : name;
        return trimmed.isBlank() ? "adjunto" : trimmed;
    }

}
