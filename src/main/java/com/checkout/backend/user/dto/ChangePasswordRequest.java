package com.checkout.backend.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Cambio de contraseña del propio usuario.
 *
 * Pide la actual ademas de la nueva, y eso no es burocracia: sin ese campo, un
 * token robado bastaria para apropiarse de la cuenta entera cambiando la
 * contraseña. Con el, quien tenga solo el token no puede cerrar la puerta por
 * dentro.
 *
 * La nueva contraseña repite las mismas reglas que el registro. Estan duplicadas
 * a proposito y no extraidas a una anotacion comun: son dos sitios donde una
 * regla podria divergir legitimamente —endurecer el cambio sin romper a los ya
 * registrados— y unirlas ahora seria adivinar que nunca pasara.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ChangePasswordRequest {

    @NotBlank(message = "Hay que indicar la contrasena actual.")
    private String currentPassword;

    @NotBlank(message = "La contrasena nueva es obligatoria.")
    @Size(min = 8, max = 100, message = "La contrasena debe tener entre 8 y 100 caracteres.")
    @Pattern(
            regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9]).+$",
            message = "La contrasena debe incluir minuscula, mayuscula, digito y simbolo.")
    private String newPassword;
}
