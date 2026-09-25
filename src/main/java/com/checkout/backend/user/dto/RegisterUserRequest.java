package com.checkout.backend.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// Public sign-up payload. The entity stores the BCrypt hash.
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class RegisterUserRequest {
    @NotBlank @Size(max = 120)
    private String name;

    @NotBlank @Email
    @Size(max = 180)
    private String email;

    @NotBlank
    @Size(min = 8, max = 100,
            message = "The password must be at least 8 characters long")
    private String password;

    @Past
    private LocalDate birthDate;
}
