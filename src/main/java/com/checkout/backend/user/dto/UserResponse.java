package com.checkout.backend.user.dto;

import com.checkout.backend.user.model.Role;
import com.checkout.backend.user.model.UserStatus;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

// Never exposes passwordHash.
@Getter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class UserResponse {
    private Long id;
    private String name;
    private String email;
    private Set<Role> roles;
    private UserStatus status;
    private LocalDate birthDate;
    private LocalDateTime createdAt;
}