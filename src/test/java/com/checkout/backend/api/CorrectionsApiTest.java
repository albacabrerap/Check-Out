package com.checkout.backend.api;

import com.checkout.backend.support.DatabaseCleaner;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.json.JsonMapper;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Corregir lo ya registrado, y cambiar la contraseña.
 *
 * Los dos casos vienen de huecos que la auditoria encontro:
 *
 * No habia forma de corregir un ingreso o un gasto. Solo se podia borrar y volver
 * a crear, y el borrado de un ingreso valida contra el saldo disponible, asi que
 * quien escribia 5000 en vez de 500 y comprometia ese dinero en una meta se
 * quedaba sin salida: no podia borrarlo ni editarlo.
 *
 * Y no habia forma de cambiar la contraseña, que es lo primero que hace alguien
 * que sospecha que su cuenta esta comprometida.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CorrectionsApiTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String PASSWORD = "Secreto123!";

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;

    private String token;
    private String email;

    @BeforeEach
    void setUp() throws Exception {
        email = "ana" + UUID.randomUUID() + "@utec.edu.pe";
        token = accessTokenOf(register(email, PASSWORD));
    }

    @AfterEach
    void cleanUp() {
        DatabaseCleaner.clean(jdbc);
    }

    // ------------------------------------------------------------------
    // Corregir un ingreso
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Un ingreso mal escrito se puede corregir aunque su dinero este comprometido")
    void anIncomeCanBeFixedEvenWhenItsMoneyIsCommitted() throws Exception {
        // El usuario ingresa 5000 y aparta 4000 en una meta que sigue EN PROGRESO.
        // Que siga en progreso importa: committedAmount solo suma las metas
        // IN_PROGRESS, asi que una meta ya cumplida libera su acumulado y el dinero
        // vuelve a ser gastable.
        long incomeId = id(perform(post("/api/v1/incomes"), """
                {"amount": 5000.00, "source": "Sueldo", "date": "2026-09-01"}
                """));

        long goalId = id(perform(post("/api/v1/savings-goals"), """
                {"name": "Viaje", "targetAmount": 6000.00, "deadline": "2027-06-30"}
                """));

        perform(post("/api/v1/savings-goals/" + goalId + "/contributions"), """
                {"amount": 4000.00, "source": "MANUAL"}
                """).andExpect(status().isCreated());

        // Borrarlo es imposible: quitaria el respaldo de la meta. Esto ya pasaba
        // antes y sigue siendo correcto.
        mockMvc.perform(auth(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete("/api/v1/incomes/" + incomeId)))
                .andExpect(status().isBadRequest());

        // Pero corregirlo hacia arriba si se puede, y es lo que faltaba.
        perform(put("/api/v1/incomes/" + incomeId), """
                {"amount": 5200.00, "source": "Sueldo y bono", "date": "2026-09-01"}
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value(5200.00))
                .andExpect(jsonPath("$.source").value("Sueldo y bono"));

        // El saldo subio por la diferencia, no se recalculo desde cero.
        mockMvc.perform(auth(get("/api/v1/savings")))
                .andExpect(jsonPath("$.currentBalance").value(5200.00))
                .andExpect(jsonPath("$.committedAmount").value(4000.00))
                .andExpect(jsonPath("$.availableBalance").value(1200.00));
    }

    @Test
    @DisplayName("Corregir un ingreso a la baja no puede dejar una meta sin respaldo")
    void anIncomeCannotBeCorrectedBelowWhatIsCommitted() throws Exception {
        long incomeId = id(perform(post("/api/v1/incomes"), """
                {"amount": 5000.00, "source": "Sueldo", "date": "2026-09-01"}
                """));

        long goalId = id(perform(post("/api/v1/savings-goals"), """
                {"name": "Viaje", "targetAmount": 6000.00, "deadline": "2027-06-30"}
                """));

        perform(post("/api/v1/savings-goals/" + goalId + "/contributions"), """
                {"amount": 4000.00, "source": "MANUAL"}
                """).andExpect(status().isCreated());

        // Bajar de 5000 a 500 exigiria cobrar 4500, y solo hay 1000 disponibles.
        perform(put("/api/v1/incomes/" + incomeId), """
                {"amount": 500.00, "source": "Sueldo", "date": "2026-09-01"}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("disponible")));
    }

    @Test
    @DisplayName("No se puede corregir un ingreso de otro usuario")
    void anotherUsersIncomeCannotBeCorrected() throws Exception {
        long incomeId = id(perform(post("/api/v1/incomes"), """
                {"amount": 100.00, "source": "Sueldo", "date": "2026-09-01"}
                """));

        String otherToken = accessTokenOf(
                register("beto" + UUID.randomUUID() + "@utec.edu.pe", PASSWORD));

        mockMvc.perform(put("/api/v1/incomes/" + incomeId)
                        .header("Authorization", "Bearer " + otherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount": 999999.00, "source": "Robo", "date": "2026-09-01"}
                                """))
                .andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------------
    // Corregir un gasto
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Un gasto se puede corregir y el saldo se ajusta por la diferencia")
    void anExpenseCanBeCorrected() throws Exception {
        perform(post("/api/v1/incomes"), """
                {"amount": 1000.00, "source": "Sueldo", "date": "2026-09-01"}
                """);

        long expenseId = id(perform(post("/api/v1/expenses"), """
                {"category": "FOOD", "amount": 300.00, "date": "2026-09-02"}
                """));

        perform(put("/api/v1/expenses/" + expenseId), """
                {"category": "FOOD", "amount": 120.00, "date": "2026-09-02"}
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value(120.00));

        // 1000 - 120, no 1000 - 300 - 120 ni 1000 - 420.
        mockMvc.perform(auth(get("/api/v1/savings")))
                .andExpect(jsonPath("$.currentBalance").value(880.00));
    }

    // ------------------------------------------------------------------
    // Cambio de contraseña
    // ------------------------------------------------------------------

    @Test
    @DisplayName("La contraseña se cambia y cierra las demas sesiones")
    void changingThePasswordRevokesTheOtherSessions() throws Exception {
        // Un refresh token de una sesion anterior.
        String oldRefresh = fieldOf(login(email, PASSWORD), "refreshToken");

        perform(put("/api/v1/users/me/password"), """
                {"currentPassword": "Secreto123!", "newPassword": "OtroSecreto456$"}
                """).andExpect(status().isNoContent());

        // La contraseña nueva entra.
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"OtroSecreto456$\"}"))
                .andExpect(status().isOk());

        // La antigua no.
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isUnauthorized());

        // Y el refresh token de antes ya no sirve: es lo que hace que cambiar la
        // contraseña expulse de verdad a quien tuviera la sesion abierta.
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + oldRefresh + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Sin la contraseña actual no se puede cambiar, ni con un token valido")
    void theCurrentPasswordIsRequired() throws Exception {
        perform(put("/api/v1/users/me/password"), """
                {"currentPassword": "LaQueNoEs1!", "newPassword": "OtroSecreto456$"}
                """).andExpect(status().isUnauthorized());

        // La original sigue valiendo.
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("La contraseña nueva tiene que cumplir las mismas reglas que en el registro")
    void theNewPasswordMustBeStrong() throws Exception {
        perform(put("/api/v1/users/me/password"), """
                {"currentPassword": "Secreto123!", "newPassword": "sololetras"}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("newPassword"));
    }

    // ------------------------------------------------------------------
    // Ayudas
    // ------------------------------------------------------------------

    private org.springframework.test.web.servlet.ResultActions perform(
            MockHttpServletRequestBuilder builder, String body) throws Exception {
        return mockMvc.perform(auth(builder)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder builder) {
        return builder.header("Authorization", "Bearer " + token);
    }

    private String register(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Ana\",\"email\":\"" + email
                                + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
    }

    private String login(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private String accessTokenOf(String authResponse) {
        return fieldOf(authResponse, "accessToken");
    }

    private String fieldOf(String json, String field) {
        return JSON.readTree(json).get(field).asString();
    }

    private long id(org.springframework.test.web.servlet.ResultActions actions) throws Exception {
        String body = actions.andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JSON.readTree(body).get("id").asLong();
    }
}
