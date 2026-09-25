package com.checkout.backend.user.controller;

import com.checkout.backend.user.dto.ChangePasswordRequest;
import com.checkout.backend.user.dto.UpdateUserRequest;
import com.checkout.backend.user.dto.UserResponse;
import com.checkout.backend.user.service.CurrentUserProvider;
import com.checkout.backend.user.service.UserService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * La cuenta del usuario autenticado.
 *
 * La ruta es /users/me y no /users/{id}: el usuario no necesita saber su propio
 * id para consultarse, y con /{id} habria que comprobar en cada endpoint que el
 * id es el suyo. Una ruta que no admite el id de otro no puede filtrar datos de
 * otro.
 */
@RestController
@RequestMapping("/users")
public class UserController {

    private final UserService userService;
    private final CurrentUserProvider currentUser;

    public UserController(UserService userService, CurrentUserProvider currentUser) {
        this.userService = userService;
        this.currentUser = currentUser;
    }

    /** GET /api/v1/users/me */
    @GetMapping("/me")
    public ResponseEntity<UserResponse> me() {
        return ResponseEntity.ok(userService.toResponse(currentUser.requireCurrentUser()));
    }

    /**
     * PATCH /api/v1/users/me
     *
     * Es PATCH y no PUT porque los dos campos son opcionales y el que no venga
     * se deja como esta. Un PUT prometeria reemplazar el recurso completo, y
     * entonces omitir el nombre deberia borrarlo.
     */
    @PatchMapping("/me")
    public ResponseEntity<UserResponse> updateMe(@Valid @RequestBody UpdateUserRequest request) {
        return ResponseEntity.ok(
                userService.updateProfile(currentUser.requireCurrentUser(), request));
    }

    /**
     * DELETE /api/v1/users/me
     *
     * Baja logica: la cuenta queda inactiva y sus tokens revocados, pero el
     * historial financiero se conserva.
     */
    /**
     * PUT /api/v1/users/me/password
     *
     * Devuelve 204 y no el usuario: no hay nada nuevo que mostrar, y la respuesta
     * de una operacion con credenciales es mejor que no lleve cuerpo.
     *
     * Ojo al efecto: cierra todas las sesiones, incluida la que hizo la llamada
     * en sus otros dispositivos. El access token actual sigue valiendo hasta que
     * expire, porque es sin estado, pero no se podra renovar.
     */
    @PutMapping("/me/password")
    public ResponseEntity<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        userService.changePassword(currentUser.requireCurrentUser(), request);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/me")
    public ResponseEntity<Void> deleteMe() {
        userService.deactivate(currentUser.requireCurrentUser());
        return ResponseEntity.noContent().build();
    }

    /**
     * GET /api/v1/users
     *
     * Reservado a ADMIN. La proteccion por ruta de SecurityConfig solo exige
     * estar autenticado; es esta anotacion la que exige el rol, y por eso hacen
     * falta las dos capas: la ruta dice quien entra, la anotacion dice quien
     * puede ejecutar la operacion.
     *
     * Un usuario sin el rol recibe 403 con el cuerpo estandar, porque
     * @PreAuthorize lanza AccessDeniedException y el manejador global la
     * traduce.
     */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<UserResponse>> listAll() {
        return ResponseEntity.ok(userService.listAll());
    }

}
