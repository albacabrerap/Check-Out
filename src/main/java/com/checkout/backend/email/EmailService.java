package com.checkout.backend.email;

import java.util.concurrent.CompletableFuture;
import org.springframework.core.io.Resource;

public interface EmailService {
    CompletableFuture<Void> send(EmailDetails details);
    CompletableFuture<Void> sendWithAttachment(EmailDetails details, Resource attachment,
                                               String filename);
}