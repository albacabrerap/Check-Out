package com.checkout.backend.email;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record EmailRequest(
        @NotBlank
        @Size(max = 200)
        String subject,

        @NotBlank
        @Size(max = 5000)
        String body
) {}