package com.checkout.backend.email;

import com.checkout.backend.email.EmailDetails;
import java.util.concurrent.CompletableFuture;

public interface EmailService {
    CompletableFuture<Void> sendSimpleMail(EmailDetails details);
    CompletableFuture<Void> sendMailWithAttachment(EmailDetails details);
}