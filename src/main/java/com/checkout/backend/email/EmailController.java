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

// Sending email to authenticated user.

@RestController
@RequestMapping("/emails")
public class EmailController {

    private static final Logger log = LoggerFactory.getLogger(EmailController.class);

    // limit
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

    // POST /api/v1/emails
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

    // POST /api/v1/emails/with-attachment
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

    // File name remains, discards any other path.
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