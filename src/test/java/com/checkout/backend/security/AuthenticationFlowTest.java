package com.checkout.backend.security;

import com.checkout.backend.user.model.Role;
import com.checkout.backend.user.service.UserService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import com.checkout.backend.user.model.User;
import com.checkout.backend.user.repository.UserRepository;
import java.util.EnumSet;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Autenticacion completa con la cadena de filtros real.
 *
 * A diferencia del resto de los tests de la suite, este NO desactiva los
 * filtros. Es la diferencia entre probar los controllers y probar la seguridad:
 * con addFilters = false nunca se ejecutan ni el filtro JWT, ni el
 * AuthenticationEntryPoint, ni el AccessDeniedHandler, que son justo las piezas
 * que aqui importan. Un 401 que nace en un filtro no pasa por el
 * @RestControllerAdvice, asi que tampoco se podria comprobar que su cuerpo tiene
 * la forma del resto de los errores de la API.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuthenticationFlowTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtTokenProvider tokenProvider;

    @Autowired
    private UserService userService;

    /** El mismo secreto que la aplicacion, para poder firmar un token de prueba. */
    @Value("${jwt.secret}")
    private String secret;

    // ------------------------------------------------------------------
    // Registro
    // ------------------------------------------------------------------

    @Test
    @DisplayName("201: el registro devuelve los dos tokens y nunca la contrasena")
    void registerReturnsTokens() throws Exception {
        String body = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Ana","email":"ana@utec.edu.pe","password":"Secreto123!"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").exists())
                .andExpect(jsonPath("$.refreshToken").exists())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(900))
                .andExpect(jsonPath("$.user.email").value("ana@utec.edu.pe"))
                // UserResponse no tiene el campo, pero si alguien lo agregara
                // este test lo detendria.
                .andExpect(jsonPath("$.user.passwordHash").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain("Secreto123!");
    }

    @Test
    @DisplayName("La contrasena se guarda como hash BCrypt, nunca en claro")
    void passwordIsStoredHashed() throws Exception {
        register("ana@utec.edu.pe", "Secreto123!");

        User stored = userRepository.findByEmail("ana@utec.edu.pe").orElseThrow();

        assertThat(stored.getPasswordHash()).isNotEqualTo("Secreto123!");
        // $2a$ es el prefijo del formato BCrypt; el coste va justo despues.
        assertThat(stored.getPasswordHash()).startsWith("$2a$");
        assertThat(passwordEncoder.matches("Secreto123!", stored.getPasswordHash())).isTrue();
    }

    @Test
    @DisplayName("El registro asigna ROLE USER; el rol no se puede pedir en el cuerpo")
    void registrationAssignsUserRole() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Ana","email":"ana@utec.edu.pe","password":"Secreto123!",
                                 "roles":["ADMIN"],"status":"ACTIVE"}
                                """))
                .andExpect(status().isCreated());

        assertThat(userRepository.findByEmail("ana@utec.edu.pe").orElseThrow().getRoles())
                .containsExactly(Role.USER);
    }

    @Test
    @DisplayName("409: un correo ya registrado")
    void duplicateEmailIsConflict() throws Exception {
        register("ana@utec.edu.pe", "Secreto123!");

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Otra Ana","email":"ana@utec.edu.pe","password":"OtraClave1!"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    @DisplayName("400: una contrasena de menos de 8 caracteres")
    void shortPasswordIsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Ana","email":"ana@utec.edu.pe","password":"corta"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'password')]").exists());
    }

    // ------------------------------------------------------------------
    // Login
    // ------------------------------------------------------------------

    @Test
    @DisplayName("200: login correcto")
    void loginSucceeds() throws Exception {
        register("ana@utec.edu.pe", "Secreto123!");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"ana@utec.edu.pe","password":"Secreto123!"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists());
    }

    @Test
    @DisplayName("401: el mensaje es el mismo con contrasena incorrecta y con correo inexistente")
    void loginFailuresAreIndistinguishable() throws Exception {
        register("ana@utec.edu.pe", "Secreto123!");

        String wrongPassword = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"ana@utec.edu.pe","password":"equivocada"}
                                """))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        String unknownEmail = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"nadie@utec.edu.pe","password":"Secreto123!"}
                                """))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        // Si los mensajes difirieran, el login serviria para averiguar que
        // correos estan registrados.
        assertThat(messageOf(wrongPassword)).isEqualTo(messageOf(unknownEmail));
        assertThat(messageOf(wrongPassword)).doesNotContain("ana@utec.edu.pe");
    }

    // ------------------------------------------------------------------
    // El filtro y la proteccion de rutas
    // ------------------------------------------------------------------

    @Test
    @DisplayName("401: una ruta protegida sin token, con el cuerpo estandar de la API")
    void protectedRouteWithoutTokenIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/savings"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                // Este 401 nace en la cadena de filtros, no en un controller, asi
                // que solo tiene esta forma gracias al AuthenticationEntryPoint.
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.path").value("/api/v1/savings"));
    }

    @Test
    @DisplayName("200: la misma ruta con un token valido")
    void protectedRouteWithTokenSucceeds() throws Exception {
        String token = accessTokenOf(register("ana@utec.edu.pe", "Secreto123!"));

        mockMvc.perform(get("/api/v1/savings").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentBalance").value(0));
    }

    @Test
    @DisplayName("401: un token manipulado no pasa el filtro")
    void tamperedTokenIsRejected() throws Exception {
        String token = accessTokenOf(register("ana@utec.edu.pe", "Secreto123!"));
        // Cambiar un caracter de la firma invalida el token entero.
        String tampered = token.substring(0, token.length() - 1)
                + (token.endsWith("A") ? "B" : "A");

        mockMvc.perform(get("/api/v1/savings").header("Authorization", "Bearer " + tampered))
                .andExpect(status().isUnauthorized())
                // El motivo del rechazo no sale: diria a quien prueba tokens si
                // fallo la firma o la fecha.
                .andExpect(jsonPath("$.message").value(not(containsString("signature"))));
    }

    @Test
    @DisplayName("401: un encabezado sin el esquema Bearer")
    void malformedAuthorizationHeaderIsRejected() throws Exception {
        String token = accessTokenOf(register("ana@utec.edu.pe", "Secreto123!"));

        mockMvc.perform(get("/api/v1/savings").header("Authorization", token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("El esquema Bearer se acepta sin distinguir mayusculas, como pide el RFC 7235")
    void bearerSchemeIsCaseInsensitive() throws Exception {
        String token = accessTokenOf(register("ana@utec.edu.pe", "Secreto123!"));

        mockMvc.perform(get("/api/v1/savings").header("Authorization", "bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Las rutas de autenticacion son publicas")
    void authRoutesArePublic() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"nadie@utec.edu.pe","password":"loquesea"}
                                """))
                // 401 por credenciales, no por falta de token: la ruta se alcanzo.
                .andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------------------
    // Refresh y logout
    // ------------------------------------------------------------------

    @Test
    @DisplayName("El refresco entrega tokens nuevos y revoca el anterior")
    void refreshRotatesTheToken() throws Exception {
        String first = register("ana@utec.edu.pe", "Secreto123!");
        String firstRefresh = fieldOf(first, "refreshToken");

        String second = mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + firstRefresh + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(fieldOf(second, "refreshToken")).isNotEqualTo(firstRefresh);

        // El token viejo ya no sirve: eso es lo que hace que uno robado tenga
        // fecha de caducidad real.
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + firstRefresh + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    // La revocacion de la familia por reutilizacion NO se puede probar desde
    // aqui, y por eso su test vive en RefreshTokenReplayTest.
    //
    // Esta clase lleva @Transactional. La revocacion se confirma en una
    // transaccion propia (RefreshTokenFamilyRevoker, REQUIRES_NEW) para
    // sobrevivir al rollback de la excepcion que la sigue; pero el contexto de
    // persistencia de esta transaccion de test ya tiene cargadas esas filas, y
    // una consulta posterior devuelve la instancia en memoria con revokedAt en
    // null en vez de lo que la base acaba de escribir. El resultado seria un
    // test rojo con el codigo correcto.
    //
    // Antes esta misma prueba estaba aqui y pasaba en verde con el codigo roto,
    // porque sin transaccion propia la revocacion se veia dentro de la
    // transaccion del test aunque en produccion el rollback la deshiciera. Es el
    // falso positivo que encontro la auditoria integral. En los dos sentidos, el
    // veredicto de este test dependia de la transaccion del test y no del
    // comportamiento real.

    @Test
    @DisplayName("204: el logout deja el token de refresco sin efecto")
    void logoutRevokesTheRefreshToken() throws Exception {
        String refresh = fieldOf(register("ana@utec.edu.pe", "Secreto123!"), "refreshToken");

        mockMvc.perform(post("/api/v1/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refresh + "\"}"))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refresh + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------------------
    // Autorizacion por rol
    // ------------------------------------------------------------------

    @Test
    @DisplayName("403: un usuario normal no puede listar usuarios")
    void listingUsersRequiresAdmin() throws Exception {
        String token = accessTokenOf(register("ana@utec.edu.pe", "Secreto123!"));

        mockMvc.perform(get("/api/v1/users").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.message")
                        .value("No tienes permisos para realizar esta accion."));
    }

    @Test
    @DisplayName("200: un ADMIN si puede listar usuarios")
    void adminCanListUsers() throws Exception {
        userRepository.save(User.builder()
                .name("Alba")
                .email("alba@utec.edu.pe")
                .passwordHash(passwordEncoder.encode("Secreto123!"))
                .roles(EnumSet.of(Role.ADMIN))
                .build());

        String token = accessTokenOf(login("alba@utec.edu.pe", "Secreto123!"));

        mockMvc.perform(get("/api/v1/users").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Cada usuario solo ve su propio perfil en /users/me")
    void meReturnsTheTokenOwner() throws Exception {
        register("ana@utec.edu.pe", "Secreto123!");
        String betoToken = accessTokenOf(register("beto@utec.edu.pe", "Secreto123!"));

        mockMvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + betoToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("beto@utec.edu.pe"));
    }

    // ------------------------------------------------------------------
    // Contenido del token
    // ------------------------------------------------------------------

    @Test
    @DisplayName("El token lleva los tres datos de identidad: id, correo y roles")
    void tokenCarriesIdentityClaims() throws Exception {
        String token = accessTokenOf(register("ana@utec.edu.pe", "Secreto123!"));
        Long expectedId = userRepository.findByEmail("ana@utec.edu.pe").orElseThrow().getId();

        Claims claims = tokenProvider.parseToken(token);

        assertThat(claims).isNotNull();
        assertThat(tokenProvider.extractUserId(claims)).isEqualTo(expectedId);
        // El correo va en el subject, que es el campo que el estandar reserva
        // para identificar al sujeto del token.
        assertThat(claims.getSubject()).isEqualTo("ana@utec.edu.pe");
        assertThat(tokenProvider.extractRoles(claims)).containsExactly(Role.USER);
    }

    @Test
    @DisplayName("El token declara su vencimiento y uno caducado no se acepta")
    void tokenDeclaresAndEnforcesExpiration() throws Exception {
        String token = accessTokenOf(register("ana@utec.edu.pe", "Secreto123!"));
        Claims claims = tokenProvider.parseToken(token);

        assertThat(claims.getExpiration()).isAfter(claims.getIssuedAt());
        assertThat(claims.getExpiration()).isCloseTo(
                Date.from(claims.getIssuedAt().toInstant().plusSeconds(900)),
                2000);

        // Firmado con la misma clave pero ya vencido: lo unico invalido es la
        // fecha, asi que si pasa, la expiracion no se esta comprobando.
        String expired = Jwts.builder()
                .subject("ana@utec.edu.pe")
                .issuedAt(Date.from(Instant.now().minusSeconds(7200)))
                .expiration(Date.from(Instant.now().minusSeconds(3600)))
                .signWith(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)))
                .compact();

        assertThat(tokenProvider.parseToken(expired)).isNull();

        mockMvc.perform(get("/api/v1/savings").header("Authorization", "Bearer " + expired))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("El id del token identifica al usuario sin consultar por correo")
    void requestsAreResolvedByTokenUserId() throws Exception {
        register("ana@utec.edu.pe", "Secreto123!");
        String betoToken = accessTokenOf(register("beto@utec.edu.pe", "Secreto123!"));
        Long betoId = userRepository.findByEmail("beto@utec.edu.pe").orElseThrow().getId();

        Claims claims = tokenProvider.parseToken(betoToken);
        assertThat(tokenProvider.extractUserId(claims)).isEqualTo(betoId);

        mockMvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + betoToken))
                .andExpect(jsonPath("$.id").value(betoId));
    }

    // ------------------------------------------------------------------
    // Permisos en la capa de servicio
    // ------------------------------------------------------------------

    @Test
    @DisplayName("403: el servicio tambien exige ADMIN, no solo la ruta")
    void serviceLayerEnforcesTheRole() throws Exception {
        register("ana@utec.edu.pe", "Secreto123!");
        User ana = userRepository.findByEmail("ana@utec.edu.pe").orElseThrow();

        // Se llama al servicio directamente, saltandose el controller y su
        // anotacion: si la unica proteccion estuviera en la ruta, esto listaria
        // todos los usuarios.
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(
                        ana.getEmail(), null,
                        UserPrincipal.authoritiesOf(ana.getRoles())));

        assertThatThrownBy(() -> userService.listAll())
                .isInstanceOf(AccessDeniedException.class);

        SecurityContextHolder.clearContext();
    }

    // ------------------------------------------------------------------
    // Utilidades
    // ------------------------------------------------------------------

    private String register(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Usuario","email":"%s","password":"%s"}
                                """.formatted(email, password)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
    }

    private String login(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}
                                """.formatted(email, password)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private static String accessTokenOf(String authResponse) {
        return fieldOf(authResponse, "accessToken");
    }

    private static String fieldOf(String json, String field) {
        JsonNode node = JSON.readTree(json).get(field);
        return node == null ? null : node.asString();
    }

    private static String messageOf(String errorJson) {
        return fieldOf(errorJson, "message");
    }

}
