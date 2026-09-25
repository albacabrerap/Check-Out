package com.checkout.backend.user.dto;

import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// null fields are left untouched by the mapper.
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class UpdateUserRequest {
    @Size(max = 120)
    private String name;

    @Past
    private LocalDate birthDate;
}