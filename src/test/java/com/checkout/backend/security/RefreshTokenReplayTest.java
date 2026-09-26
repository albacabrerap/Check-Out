package com.checkout.backend.security;

import com.checkout.backend.support.DatabaseCleaner;
import com.checkout.backend.token_wallet.refresh_token.model.RefreshToken;
import com.checkout.backend.token_wallet.refresh_token.repository.RefreshTokenRepository;
import com.checkout.backend.token_wallet.refresh_token.service.RefreshTokenService;
import com.checkout.backend.user.repository.UserRepository;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Que la revocacion por reutilizacion quede escrita de verdad.
 *
 * Esta clase existe aparte de AuthenticationFlowTest por una sola razon, y es
 * la que la hace util: <b>no lleva {@code @Transactional}</b>.
 *
 * Con {@code @Transactional} a nivel de clase, el metodo de test es el dueno de
 * la transaccion y el servicio se une a ella en vez de abrir la suya. Cuando
 * rotate() lanza la excepcion, Spring marca la transaccion para rollback pero no
 * la revierte todavia, asi que lo escrito antes de la excepcion sigue visible
 * dentro del test. La prueba pasa aunque en produccion —donde rotate() si es el
 * dueno— ese mismo rollback borre la revocacion. Es exactamente el falso
 * positivo que encontro la auditoria integral: el test en verde y el agujero
 * abierto.
 *
 * Aqui cada peticion confirma de verdad, y la comprobacion final no se hace
 * repitiendo una llamada HTTP sino leyendo las filas: que la API responda 401 no
 * demuestra que el token quedara revocado, solo que esa peticion se rechazo.
 */
@SpringBootTest
@AutoConfigureMockMvc
class RefreshTokenReplayTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RefreshTokenService refreshTokenService;

    @Autowired
    private JdbcTemplate jdbc;

    /** Sin transaccion de test que revierta, la limpieza es explicita. */
    @AfterEach
    void cleanUp() {
        DatabaseCleaner.clean(jdbc);
    }

    @Test
    @DisplayName("Reutilizar un token rotado deja la familia revocada en la base")
    void replayRevokesTheFamilyForReal() throws Exception {
        String first = register("replay@utec.edu.pe");
        String firstRefresh = fieldOf(first, "refreshToken");

        String second = refresh(firstRefresh, 200);
        String secondRefresh = fieldOf(second, "refreshToken");

        Long userId = userRepository.findByEmail("replay@utec.edu.pe").orElseThrow().getId();
        assertThat(activeTokensOf(userId))
                .as("tras rotar deberia quedar vivo solo el token nuevo")
                .hasSize(1);

        // El token viejo reaparece: alguien tiene una copia.
        refresh(firstRefresh, 401);

        // La comprobacion que importa. Antes del arreglo, el rollback de la
        // excepcion deshacia esta revocacion y aqui seguia habiendo un token
        // vivo.
        assertThat(activeTokensOf(userId))
                .as("la reutilizacion debe dejar la familia entera revocada")
                .isEmpty();

        // Y como consecuencia, el token legitimo tampoco sirve ya.
        refresh(secondRefresh, 401);
    }

    @Test
    @DisplayName("Un canje normal revoca el token entregado y emite exactamente uno nuevo")
    void rotationLeavesASingleLiveToken() throws Exception {
        String refreshToken = fieldOf(register("rotacion@utec.edu.pe"), "refreshToken");
        Long userId = userRepository.findByEmail("rotacion@utec.edu.pe").orElseThrow().getId();

        refresh(refreshToken, 200);

        assertThat(refreshTokenRepository.findByUserId(userId))
                .as("el canje crea uno nuevo y no borra el viejo, solo lo revoca")
                .hasSize(2);
        assertThat(activeTokensOf(userId))
                .as("de los dos, solo uno puede seguir vivo")
                .hasSize(1);
    }

    @Test
    @DisplayName("Dos canjes simultaneos del mismo token: gana exactamente uno")
    void concurrentRotationHasASingleWinner() throws Exception {
        String refreshToken = fieldOf(register("carrera@utec.edu.pe"), "refreshToken");
        Long userId = userRepository.findByEmail("carrera@utec.edu.pe").orElseThrow().getId();

        // Una barrera para que entren a la vez de verdad; sin ella el primero
        // termina antes de que el segundo empiece y no se prueba nada.
        CyclicBarrier startTogether = new CyclicBarrier(2);
        ExecutorService pool = Executors.newFixedThreadPool(2);

        try {
            Callable<Boolean> rotate = () -> {
                startTogether.await();
                try {
                    refreshTokenService.rotate(refreshToken);
                    return true;
                } catch (RuntimeException rejected) {
                    return false;
                }
            };

            List<Future<Boolean>> futures = pool.invokeAll(List.of(rotate, rotate));
            long winners = 0;
            for (Future<Boolean> future : futures) {
                if (future.get()) {
                    winners++;
                }
            }

            // Sin el compare-and-set los dos leian revokedAt en null, los dos
            // escribian, y el usuario acababa con dos sesiones a partir de un
            // solo token.
            assertThat(winners)
                    .as("un token solo se puede canjear una vez")
                    .isEqualTo(1);
            assertThat(activeTokensOf(userId))
                    .as("el token canjeado queda revocado pase lo que pase")
                    .isEmpty();
        } finally {
            pool.shutdownNow();
        }
    }

    // ------------------------------------------------------------------

    private List<RefreshToken> activeTokensOf(Long userId) {
        return refreshTokenRepository.findByUserId(userId).stream()
                .filter(token -> token.getRevokedAt() == null)
                .toList();
    }

    private String register(String email) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Usuario","email":"%s","password":"Secreto123!"}
                                """.formatted(email)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
    }

    private String refresh(String token, int expectedStatus) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + token + "\"}"))
                .andExpect(status().is(expectedStatus))
                .andReturn().getResponse().getContentAsString();
    }

    private String fieldOf(String json, String field) {
        JsonNode node = JSON.readTree(json).get(field);
        assertThat(node).as("el campo %s deberia venir en la respuesta", field).isNotNull();
        return node.asString();
    }

}
