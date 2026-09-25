package com.checkout.backend.user.service;

import com.checkout.backend.exceptions.DuplicateResourceException;
import com.checkout.backend.investment_portfolio.service.PortfolioService;
import com.checkout.backend.savings.service.SavingsService;
import com.checkout.backend.security.JwtTokenProvider;
import com.checkout.backend.token_wallet.refresh_token.service.RefreshTokenService;
import com.checkout.backend.token_wallet.service.TokenWalletService;
import com.checkout.backend.user.dto.AuthResponse;
import com.checkout.backend.user.dto.LoginRequest;
import com.checkout.backend.user.dto.RegisterUserRequest;
import com.checkout.backend.user.dto.UserResponse;
import com.checkout.backend.user.model.Role;
import com.checkout.backend.user.model.User;
import com.checkout.backend.user.model.UserStatus;
import com.checkout.backend.user.repository.UserRepository;
import java.util.EnumSet;
import org.modelmapper.ModelMapper;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Registro, inicio y cierre de sesion.
 *
 * Emite dos tokens con papeles distintos. El de acceso es un JWT corto que viaja
 * en cada peticion y no se puede revocar, porque validarlo contra la base en
 * cada llamada anularia la ventaja de que sea autocontenido. El de refresco es
 * un valor opaco, largo y revocable, que solo aparece al renovar. Esa division
 * es lo que permite tener sesiones largas sin que un token robado sirva para
 * siempre.
 */
@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider tokenProvider;
    private final RefreshTokenService refreshTokenService;
    private final TokenWalletService walletService;
    private final SavingsService savingsService;
    private final PortfolioService portfolioService;
    private final ModelMapper mapper;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       AuthenticationManager authenticationManager,
                       JwtTokenProvider tokenProvider,
                       RefreshTokenService refreshTokenService,
                       TokenWalletService walletService,
                       SavingsService savingsService,
                       PortfolioService portfolioService,
                       ModelMapper mapper) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.tokenProvider = tokenProvider;
        this.refreshTokenService = refreshTokenService;
        this.walletService = walletService;
        this.savingsService = savingsService;
        this.portfolioService = portfolioService;
        this.mapper = mapper;
    }

    /**
     * Da de alta un usuario y lo deja con la sesion iniciada.
     *
     * El rol y el estado los pone el servidor, no el cuerpo de la peticion.
     * RegisterUserRequest no expone esos campos justamente para que nadie pueda
     * registrarse como ADMIN mandando un JSON con un campo de mas.
     *
     * La contrasena en claro no se guarda ni se registra en ningun log: entra
     * por el request, se convierte en hash y se descarta.
     */
    @Transactional
    public AuthResponse register(RegisterUserRequest request) {
        // Comprobar antes da un mensaje entendible; el UNIQUE de la tabla es la
        // red para dos registros simultaneos, y su violacion termina en el mismo
        // 409 por el manejador global.
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateResourceException("Ya existe una cuenta con ese correo.");
        }

        User user = userRepository.save(User.builder()
                .name(request.getName())
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .birthDate(request.getBirthDate())
                .roles(EnumSet.of(Role.USER))
                .status(UserStatus.ACTIVE)
                .build());

        // Las tres cuentas 1:1 del usuario se crean aqui, en la misma transaccion
        // que el usuario. Antes se creaban de forma diferida, en el primer acceso a
        // cada modulo, y eso dejaba una carrera: dos peticiones simultaneas de un
        // usuario nuevo podian ver las dos que su monedero no existia, y la que
        // perdia recibia un 409 por el UNIQUE hablando de un conflicto que el
        // usuario no habia provocado.
        //
        // Crearlas al registrar cierra esa ventana, y ademas hace que el estado de
        // un usuario recien creado sea completo en vez de depender de por donde
        // entre primero. Si falla cualquiera de las tres, no queda usuario a medias:
        // es una sola transaccion.
        walletService.getOrCreate(user);
        savingsService.getOrCreate(user);
        portfolioService.getOrCreate(user);

        return buildAuthResponse(user);
    }

    /**
     * Inicia sesion.
     *
     * La comparacion la hace el AuthenticationManager y no este metodo: ahi se
     * hace en tiempo constante y se aplican de paso los estados de la cuenta,
     * bloqueada o deshabilitada, que UserPrincipal traduce.
     *
     * Cualquier fallo sale como AuthenticationException y el manejador global lo
     * convierte en un 401 sin detalle. Distinguir "no existe" de "contrasena
     * incorrecta" convertiria el login en un verificador de que correos estan
     * registrados.
     */
    @Transactional
    public AuthResponse login(LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword()));

        User user = userRepository.findByEmail(authentication.getName()).orElseThrow();
        return buildAuthResponse(user);
    }

    /**
     * Cambia un token de refresco por un par nuevo.
     *
     * La rotacion y la deteccion de reutilizacion viven en RefreshTokenService.
     */
    @Transactional
    public AuthResponse refresh(String refreshToken) {
        return buildAuthResponse(refreshTokenService.rotate(refreshToken));
    }

    /**
     * Cierra la sesion revocando el token de refresco.
     *
     * El token de acceso sigue siendo valido hasta que expire: es el precio de
     * no consultar la base en cada peticion. Por eso su vida es de minutos.
     */
    @Transactional
    public void logout(String refreshToken) {
        refreshTokenService.revoke(refreshToken);
    }

    private AuthResponse buildAuthResponse(User user) {
        return AuthResponse.builder()
                .accessToken(tokenProvider.generateAccessToken(user))
                .refreshToken(refreshTokenService.issue(user))
                .tokenType("Bearer")
                .expiresIn(tokenProvider.accessTokenExpiresInSeconds())
                .user(mapper.map(user, UserResponse.class))
                .build();
    }

}
