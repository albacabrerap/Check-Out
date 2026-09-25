package com.checkout.backend.api;

import com.checkout.backend.user.model.Role;
import com.checkout.backend.user.model.User;
import com.checkout.backend.user.repository.UserRepository;
import java.util.EnumSet;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import com.checkout.backend.support.DatabaseCleaner;
import org.junit.jupiter.api.AfterEach;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Proyecciones, monedero de fichas, minijuegos e inversion, de punta a punta.
 *
 * Corre con la cadena de seguridad real y tokens de verdad, no con un doble de
 * CurrentUserProvider. Es necesario: buena parte de lo que hay que comprobar
 * aqui son los @PreAuthorize que separan al jugador del administrador, y esos
 * solo se ejecutan si hay un SecurityContext poblado por el filtro JWT.
 *
 * El foco no es que los endpoints respondan, sino que las tres decisiones de
 * seguridad del bloque se sostengan: que el monedero no se pueda acreditar, que
 * el catalogo no se pueda editar desde una cuenta normal, y que una puntuacion
 * inventada no se convierta en fichas ilimitadas.
 *
 * Esta clase NO lleva @Transactional, y es deliberado. Con esa anotacion el
 * metodo de test se convierte en el dueno de la transaccion externa y los
 * servicios se le unen como participantes, de modo que el commit que la
 * aplicacion hace en produccion aqui no ocurre. Eso ya escondio un fallo real:
 * las ordenes rechazadas devolvian 500 por UnexpectedRollbackException y el test
 * de mas abajo pasaba en verde igualmente. Sin la anotacion, cada test commitea
 * de verdad y la limpieza se hace en el @AfterEach.
 */
@SpringBootTest
@AutoConfigureMockMvc
class RemainingModulesApiTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String PASSWORD = "Secreto123!";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbc;

    private String anaToken;
    private String adminToken;

    @BeforeEach
    void setUp() throws Exception {
        anaToken = accessTokenOf(register("ana@utec.edu.pe"));

        userRepository.save(User.builder()
                .name("Alba")
                .email("alba@utec.edu.pe")
                .passwordHash(passwordEncoder.encode(PASSWORD))
                .roles(EnumSet.of(Role.ADMIN))
                .build());
        adminToken = accessTokenOf(login("alba@utec.edu.pe"));
    }

    /**
     * Sustituye al rollback que antes daba @Transactional. Los tests commitean de
     * verdad, asi que la base hay que vaciarla explicitamente.
     */
    @AfterEach
    void cleanUp() {
        DatabaseCleaner.clean(jdbc);
    }

    // ------------------------------------------------------------------
    // Monedero de fichas: sin escritura
    // ------------------------------------------------------------------

    @Test
    @DisplayName("El monedero arranca en cero y no expone ninguna forma de acreditarse fichas")
    void theWalletIsReadOnly() throws Exception {
        mockMvc.perform(auth(get("/api/v1/token-wallet")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenBalance").value(0));

        // 405 y no 404: la ruta existe, lo que no existe es el verbo. Si alguna
        // vez alguien agrega un POST aqui, este test lo detiene.
        mockMvc.perform(auth(post("/api/v1/token-wallet"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tokenBalance\": 999999}"))
                .andExpect(status().isMethodNotAllowed());

        mockMvc.perform(auth(put("/api/v1/token-wallet"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tokenBalance\": 999999}"))
                .andExpect(status().isMethodNotAllowed());

        mockMvc.perform(auth(delete("/api/v1/token-wallet")))
                .andExpect(status().isMethodNotAllowed());
    }

    // ------------------------------------------------------------------
    // Catalogo de minijuegos: lectura para todos, escritura de ADMIN
    // ------------------------------------------------------------------

    @Test
    @DisplayName("403: un jugador no puede crear ni editar minijuegos")
    void aPlayerCannotTouchTheCatalogue() throws Exception {
        mockMvc.perform(auth(post("/api/v1/minigames"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(minigameJson("Trivia gratis", "0", "1000", "PUBLISHED")))
                .andExpect(status().isForbidden());

        long id = createMinigame("Trivia de ahorro", "10", "50", "PUBLISHED");

        mockMvc.perform(auth(put("/api/v1/minigames/" + id))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(minigameJson("Trivia de ahorro", "0", "9999", "PUBLISHED")))
                .andExpect(status().isForbidden());

        mockMvc.perform(auth(delete("/api/v1/minigames/" + id)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("404: un borrador no es visible para un jugador")
    void draftsAreInvisibleToPlayers() throws Exception {
        long draft = createMinigame("Borrador", "5", "20", "DRAFT");

        mockMvc.perform(auth(get("/api/v1/minigames/" + draft)))
                .andExpect(status().isNotFound());

        mockMvc.perform(auth(get("/api/v1/minigames")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));

        // El administrador si lo ve, pidiendo la variante completa del listado.
        mockMvc.perform(admin(get("/api/v1/minigames").param("includeUnpublished", "true")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));

        // Y un jugador no puede pedirla: el @PreAuthorize del servicio la protege
        // aunque el parametro sea del mismo endpoint publico.
        mockMvc.perform(auth(get("/api/v1/minigames").param("includeUnpublished", "true")))
                .andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------------
    // La puntuacion viene del cliente: el tope es lo que sostiene la economia
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Una puntuacion inventada no paga mas que una partida perfecta")
    void anInflatedScoreIsCapped() throws Exception {
        long gratis = createMinigame("Trivia gratis", "0", "40", "PUBLISHED");

        mockMvc.perform(auth(post("/api/v1/minigame-sessions"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"minigameId\": " + gratis + ", \"score\": 999999}"))
                .andExpect(status().isCreated())
                // 40 es el maxTokenReward del juego, no una fraccion de 999999.
                .andExpect(jsonPath("$.tokensEarned").value(40.00));

        mockMvc.perform(auth(get("/api/v1/token-wallet")))
                .andExpect(jsonPath("$.tokenBalance").value(40.00));
    }

    @Test
    @DisplayName("La recompensa es proporcional a la puntuacion")
    void theRewardIsProportional() throws Exception {
        long gratis = createMinigame("Trivia gratis", "0", "40", "PUBLISHED");

        mockMvc.perform(auth(post("/api/v1/minigame-sessions"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"minigameId\": " + gratis + ", \"score\": 50}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tokensEarned").value(20.00));
    }

    @Test
    @DisplayName("400: jugar sin fichas suficientes para pagar el coste")
    void playingWithoutTokensIsRejected() throws Exception {
        long caro = createMinigame("Simulador premium", "500", "600", "PUBLISHED");

        mockMvc.perform(auth(post("/api/v1/minigame-sessions"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"minigameId\": " + caro + ", \"score\": 80}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("fichas")));
    }

    @Test
    @DisplayName("El libro de movimientos explica de donde salio cada ficha")
    void everyTokenIsTraceable() throws Exception {
        long gratis = createMinigame("Trivia gratis", "0", "40", "PUBLISHED");
        play(gratis, 100);

        // El listado esta paginado, asi que las filas van en $.content.
        mockMvc.perform(auth(get("/api/v1/token-wallet/transactions")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].reason").value("MINIGAME"))
                .andExpect(jsonPath("$.content[0].amount").value(40.00))
                .andExpect(jsonPath("$.content[0].balanceAfter").value(40.00));
    }

    // ------------------------------------------------------------------
    // Inversion
    // ------------------------------------------------------------------

    @Test
    @DisplayName("403: un jugador no puede crear activos ni mover precios")
    void aPlayerCannotMovePrices() throws Exception {
        mockMvc.perform(auth(post("/api/v1/assets"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"symbol":"FAKE","name":"Inventado","type":"STOCK","currency":"USD"}
                                """))
                .andExpect(status().isForbidden());

        long assetId = createAsset("VOO", "Vanguard S&P 500");

        // El precio es la base con la que se valora la cartera de todos.
        mockMvc.perform(auth(put("/api/v1/assets/" + assetId + "/quote"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"price\": 99999}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Una compra descuenta fichas, abre posicion y valora la cartera")
    void buyingOpensAPosition() throws Exception {
        long assetId = createAsset("VOO", "Vanguard S&P 500");
        setQuote(assetId, "100.00");
        earnTokens(1000);

        mockMvc.perform(auth(post("/api/v1/orders"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderJson(UUID.randomUUID(), assetId, "BUY", "5")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("EXECUTED"))
                .andExpect(jsonPath("$.symbol").value("VOO"))
                .andExpect(jsonPath("$.tokensMoved").value(500.00));

        mockMvc.perform(auth(get("/api/v1/portfolio")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.positions", hasSize(1)))
                .andExpect(jsonPath("$.positions[0].symbol").value("VOO"))
                .andExpect(jsonPath("$.positions[0].quantity").value(5))
                .andExpect(jsonPath("$.positions[0].marketValue").value(500.00))
                .andExpect(jsonPath("$.investedTokens").value(500.00))
                .andExpect(jsonPath("$.unrealizedPnl").value(0));

        mockMvc.perform(auth(get("/api/v1/token-wallet")))
                .andExpect(jsonPath("$.tokenBalance").value(500.00));
    }

    @Test
    @DisplayName("Si el precio sube, la cartera refleja la ganancia no realizada")
    void aPriceRiseShowsAsUnrealisedGain() throws Exception {
        long assetId = createAsset("VOO", "Vanguard S&P 500");
        setQuote(assetId, "100.00");
        earnTokens(1000);
        placeOrder(assetId, "BUY", "5");

        setQuote(assetId, "120.00");

        mockMvc.perform(auth(get("/api/v1/portfolio")))
                .andExpect(jsonPath("$.positions[0].marketValue").value(600.00))
                .andExpect(jsonPath("$.unrealizedPnl").value(100.00));
    }

    @Test
    @DisplayName("Reintentar la misma orden no compra dos veces")
    void placingTheSameOrderTwiceIsIdempotent() throws Exception {
        long assetId = createAsset("VOO", "Vanguard S&P 500");
        setQuote(assetId, "100.00");
        earnTokens(1000);

        UUID clientOrderId = UUID.randomUUID();
        String body = orderJson(clientOrderId, assetId, "BUY", "3");

        String first = mockMvc.perform(auth(post("/api/v1/orders"))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String second = mockMvc.perform(auth(post("/api/v1/orders"))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        // La misma orden, no una nueva: el reintento devuelve la que ya existia.
        assertThat(fieldOf(first, "id")).isEqualTo(fieldOf(second, "id"));

        mockMvc.perform(auth(get("/api/v1/orders")))
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.totalElements").value(1));

        // Y se cobro una sola vez.
        mockMvc.perform(auth(get("/api/v1/token-wallet")))
                .andExpect(jsonPath("$.tokenBalance").value(700.00));
    }

    @Test
    @DisplayName("Una orden sin fichas queda registrada como rechazada, con su motivo")
    void anUnaffordableOrderIsRecordedAsRejected() throws Exception {
        long assetId = createAsset("VOO", "Vanguard S&P 500");
        setQuote(assetId, "100.00");

        mockMvc.perform(auth(post("/api/v1/orders"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderJson(UUID.randomUUID(), assetId, "BUY", "5")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.rejectionReason").exists())
                .andExpect(jsonPath("$.tokensMoved").value(0));

        mockMvc.perform(auth(get("/api/v1/portfolio")))
                .andExpect(jsonPath("$.positions", hasSize(0)));
    }

    @Test
    @DisplayName("No se puede vender lo que no se tiene")
    void sellingWithoutAPositionIsRejected() throws Exception {
        long assetId = createAsset("VOO", "Vanguard S&P 500");
        setQuote(assetId, "100.00");

        mockMvc.perform(auth(post("/api/v1/orders"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderJson(UUID.randomUUID(), assetId, "SELL", "1")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("REJECTED"));

        // Y no se acreditaron fichas por una venta que no ocurrio.
        mockMvc.perform(auth(get("/api/v1/token-wallet")))
                .andExpect(jsonPath("$.tokenBalance").value(0));
    }

    @Test
    @DisplayName("Vender entera una posicion la cierra y devuelve las fichas")
    void sellingEverythingClosesThePosition() throws Exception {
        long assetId = createAsset("VOO", "Vanguard S&P 500");
        setQuote(assetId, "100.00");
        earnTokens(1000);
        placeOrder(assetId, "BUY", "5");

        setQuote(assetId, "120.00");
        placeOrder(assetId, "SELL", "5");

        mockMvc.perform(auth(get("/api/v1/portfolio")))
                .andExpect(jsonPath("$.positions", hasSize(0)))
                .andExpect(jsonPath("$.investedTokens").value(0));

        // 1000 - 500 de la compra + 600 de la venta a 120.
        mockMvc.perform(auth(get("/api/v1/token-wallet")))
                .andExpect(jsonPath("$.tokenBalance").value(1100.00));
    }

    @Test
    @DisplayName("La cartera es de solo lectura")
    void thePortfolioIsReadOnly() throws Exception {
        mockMvc.perform(auth(post("/api/v1/portfolio/positions"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\": 100}"))
                .andExpect(status().isMethodNotAllowed());
    }

    // ------------------------------------------------------------------
    // Proyecciones
    // ------------------------------------------------------------------

    @Test
    @DisplayName("El interes compuesto supera al simple, y la diferencia se expone")
    void compoundBeatsSimple() throws Exception {
        String body = mockMvc.perform(auth(post("/api/v1/projections"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Jubilacion","initialCapital":10000.00,
                                 "monthlyContribution":200.00,"annualRate":0.08,"periods":120}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.totalContributed").value(34000.00))
                .andReturn().getResponse().getContentAsString();

        double simple = Double.parseDouble(fieldOf(body, "simpleFinalAmount"));
        double compound = Double.parseDouble(fieldOf(body, "compoundFinalAmount"));
        double difference = Double.parseDouble(fieldOf(body, "difference"));

        assertThat(compound).isGreaterThan(simple);
        assertThat(difference).isEqualTo(compound - simple, org.assertj.core.data.Offset.offset(0.01));
        // Con tasa positiva, los dos superan lo aportado.
        assertThat(simple).isGreaterThan(34000.00);
    }

    @Test
    @DisplayName("Con tasa cero la proyeccion es exactamente lo aportado")
    void aZeroRateReturnsTheContributions() throws Exception {
        mockMvc.perform(auth(post("/api/v1/projections"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Sin interes","initialCapital":1000.00,
                                 "monthlyContribution":100.00,"annualRate":0.0,"periods":12}
                                """))
                .andExpect(status().isCreated())
                // La formula del compuesto divide entre la tasa: con cero se
                // indefine, y este caso comprueba que se trata aparte.
                .andExpect(jsonPath("$.compoundFinalAmount").value(2200.00))
                .andExpect(jsonPath("$.simpleFinalAmount").value(2200.00))
                .andExpect(jsonPath("$.difference").value(0));
    }

    @Test
    @DisplayName("404: la proyeccion de otro usuario no existe para mi")
    void projectionsAreIsolatedPerUser() throws Exception {
        String body = mockMvc.perform(auth(post("/api/v1/projections"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Mia","initialCapital":100.00,
                                 "monthlyContribution":0.00,"annualRate":0.05,"periods":12}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String betoToken = accessTokenOf(register("beto@utec.edu.pe"));

        mockMvc.perform(get("/api/v1/projections/" + fieldOf(body, "id"))
                        .header("Authorization", "Bearer " + betoToken))
                .andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------------
    // Utilidades
    // ------------------------------------------------------------------

    private MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder builder) {
        return builder.header("Authorization", "Bearer " + anaToken);
    }

    private MockHttpServletRequestBuilder admin(MockHttpServletRequestBuilder builder) {
        return builder.header("Authorization", "Bearer " + adminToken);
    }

    private long createMinigame(String title, String cost, String reward, String status)
            throws Exception {
        String body = mockMvc.perform(admin(post("/api/v1/minigames"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(minigameJson(title, cost, reward, status)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return Long.parseLong(fieldOf(body, "id"));
    }

    private static String minigameJson(String title, String cost, String reward, String status) {
        return """
                {"title":"%s","type":"TRIVIA","topic":"ahorro",
                 "tokenCost":%s,"maxTokenReward":%s,"status":"%s"}
                """.formatted(title, cost, reward, status);
    }

    private long createAsset(String symbol, String name) throws Exception {
        String body = mockMvc.perform(admin(post("/api/v1/assets"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"symbol":"%s","name":"%s","type":"ETF","currency":"USD"}
                                """.formatted(symbol, name)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return Long.parseLong(fieldOf(body, "id"));
    }

    private void setQuote(long assetId, String price) throws Exception {
        mockMvc.perform(admin(put("/api/v1/assets/" + assetId + "/quote"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"price\": " + price + "}"))
                .andExpect(status().isOk());
    }

    /** Da fichas a Ana jugando, que es la unica via que existe para conseguirlas. */
    private void earnTokens(int amount) throws Exception {
        long premio = createMinigame("Premio " + amount, "0", String.valueOf(amount), "PUBLISHED");
        play(premio, 100);
    }

    private void play(long minigameId, int score) throws Exception {
        mockMvc.perform(auth(post("/api/v1/minigame-sessions"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"minigameId\": " + minigameId + ", \"score\": " + score + "}"))
                .andExpect(status().isCreated());
    }

    private void placeOrder(long assetId, String side, String quantity) throws Exception {
        mockMvc.perform(auth(post("/api/v1/orders"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderJson(UUID.randomUUID(), assetId, side, quantity)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("EXECUTED"));
    }

    private static String orderJson(UUID clientOrderId, long assetId, String side, String quantity) {
        return """
                {"clientOrderId":"%s","assetId":%d,"side":"%s","quantity":%s}
                """.formatted(clientOrderId, assetId, side, quantity);
    }

    private String register(String email) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Usuario","email":"%s","password":"%s"}
                                """.formatted(email, PASSWORD)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
    }

    private String login(String email) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}
                                """.formatted(email, PASSWORD)))
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

}
