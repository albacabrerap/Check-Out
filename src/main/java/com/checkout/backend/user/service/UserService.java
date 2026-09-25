package com.checkout.backend.user.service;

import com.checkout.backend.exceptions.UnauthenticatedException;
import com.checkout.backend.token_wallet.refresh_token.service.RefreshTokenService;
import com.checkout.backend.user.dto.ChangePasswordRequest;
import com.checkout.backend.user.dto.UpdateUserRequest;
import com.checkout.backend.user.dto.UserResponse;
import com.checkout.backend.user.model.User;
import com.checkout.backend.user.model.UserStatus;
import com.checkout.backend.user.repository.UserRepository;
import java.util.List;
import org.modelmapper.ModelMapper;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Consulta y mantenimiento de la cuenta.
 */
@Service
public class UserService {

    private final UserRepository userRepository;
    private final RefreshTokenService refreshTokenService;
    private final PasswordEncoder passwordEncoder;
    private final ModelMapper mapper;

    public UserService(UserRepository userRepository,
                       RefreshTokenService refreshTokenService,
                       PasswordEncoder passwordEncoder,
                       ModelMapper mapper) {
        this.userRepository = userRepository;
        this.refreshTokenService = refreshTokenService;
        this.passwordEncoder = passwordEncoder;
        this.mapper = mapper;
    }

    public UserResponse toResponse(User user) {
        return mapper.map(user, UserResponse.class);
    }

    /**
     * Actualiza los campos que el usuario puede cambiar de si mismo.
     *
     * UpdateUserRequest no incluye correo, roles ni estado, y eso es lo que
     * impide que alguien se ascienda a ADMIN con un campo de mas en el JSON. Los
     * dos campos son opcionales: un null significa "no lo toques", no "borralo".
     */
    @Transactional
    public UserResponse updateProfile(User user, UpdateUserRequest request) {
        if (request.getName() != null) {
            user.setName(request.getName());
        }
        if (request.getBirthDate() != null) {
            user.setBirthDate(request.getBirthDate());
        }
        return toResponse(userRepository.save(user));
    }

    /**
     * Cambia la contraseña del propio usuario.
     *
     * Dos decisiones que van juntas:
     *
     * La contraseña actual se verifica aunque quien llama ya venga autenticado.
     * El token dice que alguien entro con esa cuenta, no que sea su dueno; si un
     * access token robado bastara para cambiar la contraseña, robar un token
     * equivaldria a quedarse la cuenta.
     *
     * Y al cambiarla se revocan todos los refresh tokens. Cambiar la contraseña es
     * lo que hace alguien que sospecha que su cuenta esta comprometida, y si las
     * sesiones abiertas siguieran renovandose, ese gesto no serviria para nada:
     * quien tuviera un refresh token seguiria dentro treinta dias. El precio es
     * que el propio usuario tiene que volver a entrar en sus otros dispositivos,
     * que es exactamente lo que se espera de este cambio.
     */
    @Transactional
    public void changePassword(User user, ChangePasswordRequest request) {
        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPasswordHash())) {
            throw new UnauthenticatedException("La contrasena actual no es correcta.");
        }

        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);

        refreshTokenService.revokeAll(user);
    }

    /**
     * Da de baja la cuenta sin borrarla.
     *
     * Es baja logica porque la fila esta referenciada por ahorros, metas,
     * ingresos y gastos: borrarla destruiria el historial financiero o romperia
     * las claves foraneas. El correo sigue siendo unico entre todos los estados,
     * de modo que volver es reactivar y no registrarse otra vez.
     *
     * Revocar los tokens es parte de la baja: sin eso la cuenta queda inactiva
     * pero cualquier refresh token emitido seguiria sirviendo para renovar.
     */
    @Transactional
    public void deactivate(User user) {
        user.setStatus(UserStatus.INACTIVE);
        userRepository.save(user);
        refreshTokenService.revokeAll(user);
    }

    /**
     * Listado completo de usuarios.
     *
     * El control de rol esta aqui ademas de en el controller, y la duplicacion
     * es deliberada. La anotacion del controller protege una ruta; esta protege
     * la operacion. El dia que otro punto del codigo llame a este metodo — una
     * tarea programada, un endpoint nuevo, un listener de eventos — heredara la
     * restriccion sin que nadie tenga que acordarse de repetirla.
     */
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional(readOnly = true)
    public List<UserResponse> listAll() {
        return userRepository.findAll().stream().map(this::toResponse).toList();
    }

}
