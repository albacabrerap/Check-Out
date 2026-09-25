package com.checkout.backend.email;
import com.checkout.backend.config.AsyncFailureHandler;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@Slf4j
public class EmailController {
    private final EmailService emailService;
    private final AsyncFailureHandler failureHandler;

    public EmailController(EmailService emailService, AsyncFailureHandler failureHandler) {
        this.emailService = emailService;
        this.failureHandler = failureHandler;
    }


    @PostMapping("/sendMail")
    public ResponseEntity<String> sendMail(@RequestBody @Valid EmailDetails details) {
        log.info("[request] thread={} received mail for {}",
                Thread.currentThread().getName(), details.getRecipient());

        emailService.sendSimpleMail(details)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        failureHandler.handle("SendSimpleMail to", details.getRecipient(), ex);
                    } else {
                        log.info("[callback] thread={} mail sent to {}",
                                Thread.currentThread().getName(), details.getRecipient());
                    }
                });
        log.info("[request] thread={} returning 202", Thread.currentThread().getName());
        return ResponseEntity.accepted().body("Mail queued");
    }

    @PostMapping("/sendEmailWithAttatchment")
    public ResponseEntity<String> sendMailWithAttachment(@RequestBody EmailDetails details) {
        log.info("[request] thread={} received mail with attachment for {}",
                Thread.currentThread().getName(), details.getRecipient());

        emailService.sendMailWithAttachment(details)
                .whenComplete((result, ex) -> {
            if (ex != null) {
                failureHandler.handle("SendSimpleMail to", details.getRecipient(), ex);
            } else {
                log.info("[callback] thread={} mail with attachment sent to {}",
                        Thread.currentThread().getName(), details.getRecipient());
            }
        });

        log.info("[request] thread={} returning 202", Thread.currentThread().getName());
        return ResponseEntity.accepted().body("Mail queued");
    }
}