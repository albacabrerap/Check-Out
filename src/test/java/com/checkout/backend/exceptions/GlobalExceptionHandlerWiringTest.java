package com.checkout.backend.exceptions;

import com.checkout.backend.token_wallet.model.TokenWallet;
import com.checkout.backend.web.ApiVersioningConfig;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Comprueba el advice dentro de un contexto real de Spring, no montado a mano.
 *
 * Es la contraparte de GlobalExceptionHandlerTest, que instancia el handler con
 * standaloneSetup y por eso solo puede probar su logica. Ese arreglo tiene un
 * punto ciego grande: pasaria igual si @RestControllerAdvice no estuviera puesto,
 * porque el test registra el advice el mismo. Aca no se registra nada: si la
 * anotacion falta o el paquete queda fuera del component scan, estos tests
 * empiezan a ver el formato de error por defecto de Spring y fallan.
 *
 * Lo segundo que solo se puede comprobar aca son las excepciones que nacen antes
 * de entrar al controller — negociacion de contenido, binding de parametros,
 * validacion de metodo, rutas inexistentes. Ninguna se puede lanzar a mano de
 * forma fiel: hay que hacer el request y dejar que el DispatcherServlet real las
 * produzca. Cuatro de ellas devolvian 500 hasta que estos tests las expusieron.
 *
 * Y lo tercero es la serializacion: el ObjectMapper de verdad, con la
 * configuracion de Boot, es el que decide como sale LocalDateTime.
 *
 * addFilters = false saca la cadena de filtros de Spring Security. Hoy el
 * proyecto no tiene SecurityFilterChain propio (es el issue #5), asi que la
 * configuracion por defecto de Boot protege todo y cada request moriria en un
 * 401 antes de llegar al dispatcher. Lo que se prueba aca es el advice, no la
 * seguridad.
 */
/*
 * Contexto completo y no un slice de @WebMvcTest.
 *
 * El slice parecia lo correcto por ser mas liviano, pero elige que beans carga
 * por tipo: se lleva los filtros, porque son parte de la capa web, y deja fuera
 * los @Component normales de los que esos filtros dependen. El resultado es que
 * cada pieza nueva de la aplicacion puede romper este test por una razon que no
 * tiene nada que ver con lo que prueba, y ya paso dos veces: primero con los
 * services de los controllers, despues con el JwtTokenProvider del filtro JWT.
 *
 * Con el contexto completo eso no puede ocurrir: si la aplicacion arranca, este
 * test arranca. Sigue probando exactamente lo mismo, y de hecho con mas valor,
 * porque ahora el advice se comprueba en el contexto real y no en un recorte.
 *
 * addFilters = false quita la cadena de seguridad: lo que se prueba aqui es el
 * manejo de excepciones, y la seguridad tiene su propio test que si la levanta.
 */
@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
class GlobalExceptionHandlerWiringTest {

    /**
     * Prefijo que ApiVersioningConfig antepone a todo @RestController del
     * proyecto, este controller de prueba incluido. Se compone con la constante
     * en vez de escribir "/api/v1" a mano para que el dia que la version cambie
     * este test no se quede pidiendo la ruta vieja.
     */
    private static final String BASE = ApiVersioningConfig.API_V1;

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("El advice esta registrado en el contexto: un 404 propio sale con el DTO, no con el formato de Spring")
    void adviceIsRegisteredInTheRealContext() throws Exception {
        mockMvc.perform(get(BASE + "/wiring/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Usuario no encontrado: 7"))
                .andExpect(jsonPath("$.path").value(BASE + "/wiring/not-found"))
                // Estas dos claves son del formato por defecto de Spring. Si
                // aparecen, el advice no intervino.
                .andExpect(jsonPath("$.type").doesNotExist())
                .andExpect(jsonPath("$.detail").doesNotExist());
    }

    @Test
    @DisplayName("El ObjectMapper de Boot serializa timestamp como ISO-8601, no como numero")
    void timestampIsSerializedAsIso8601() throws Exception {
        mockMvc.perform(get(BASE + "/wiring/not-found"))
                .andExpect(jsonPath("$.timestamp")
                        .value(matchesPattern("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}.*")));
    }

    @Test
    @DisplayName("400: la validacion de un parametro de metodo la produce el framework, no una excepcion lanzada a mano")
    void methodParameterValidation() throws Exception {
        mockMvc.perform(get(BASE + "/wiring/min").param("page", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("La solicitud contiene parametros invalidos."))
                .andExpect(jsonPath("$.fieldErrors", hasSize(1)))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("page"));
    }

    @Test
    @DisplayName("400: un @Valid sobre el body enumera los dos campos rechazados")
    void bodyValidation() throws Exception {
        mockMvc.perform(post(BASE + "/wiring/body")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\",\"email\":\"no-es-un-correo\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors", hasSize(2)))
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'name')]", hasSize(1)))
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'email')]", hasSize(1)));
    }

    @Test
    @DisplayName("400: JSON mal formado, sin filtrar el mensaje de Jackson")
    void malformedJson() throws Exception {
        mockMvc.perform(post(BASE + "/wiring/body")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("El cuerpo de la solicitud no se pudo leer. Revisa que sea JSON valido."));
    }

    @Test
    @DisplayName("415: un Content-Type que la API no lee, y se listan los que si")
    void unsupportedMediaType() throws Exception {
        mockMvc.perform(post(BASE + "/wiring/body")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("hola"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.status").value(415))
                .andExpect(jsonPath("$.message").value(containsString("text/plain")))
                .andExpect(jsonPath("$.message").value(containsString("application/json")));
    }

    @Test
    @DisplayName("400: falta un parametro obligatorio y se lo nombra")
    void missingParameter() throws Exception {
        mockMvc.perform(get(BASE + "/wiring/min"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Falta el parametro obligatorio 'page'."));
    }

    @Test
    @DisplayName("400: falta una cabecera obligatoria y se la nombra")
    void missingHeader() throws Exception {
        mockMvc.perform(get(BASE + "/wiring/header"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Falta la cabecera obligatoria 'X-Tenant'."));
    }

    @Test
    @DisplayName("400: un id que no es numero se rechaza en el binding")
    void typeMismatch() throws Exception {
        mockMvc.perform(get(BASE + "/wiring/typed/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("'abc'")))
                .andExpect(jsonPath("$.message").value(containsString("Long")));
    }

    @Test
    @DisplayName("404: una ruta que no existe sale con el mismo formato que el 404 de recurso")
    void unknownRoute() throws Exception {
        mockMvc.perform(get(BASE + "/wiring/no-existe"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("La ruta solicitada no existe."))
                .andExpect(jsonPath("$.path").value(BASE + "/wiring/no-existe"));
    }

    @Test
    @DisplayName("405: la ruta existe pero no para ese verbo")
    void methodNotAllowed() throws Exception {
        mockMvc.perform(post(BASE + "/wiring/min"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.message").value("El metodo POST no esta permitido en esta ruta."));
    }

    @Test
    @DisplayName("500: una excepcion inesperada sale enmascarada tambien con el dispatcher real")
    void unexpectedIsMasked() throws Exception {
        mockMvc.perform(get(BASE + "/wiring/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message")
                        .value("Ocurrio un error interno. Intentalo de nuevo mas tarde."));
    }

    /**
     * El bloqueo optimista es la defensa contra el doble gasto, y antes de este
     * handler su exito se le presentaba al usuario como un 500. No es un fallo del
     * servidor: es una operacion que no se aplico y que se puede reintentar.
     */
    @Test
    @DisplayName("409: un conflicto de bloqueo optimista no es un 500")
    void optimisticLockIsAConflict() throws Exception {
        mockMvc.perform(get(BASE + "/wiring/concurrent"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message")
                        .value("Otra operacion modifico estos datos al mismo tiempo. "
                                + "Vuelve a intentarlo."));
    }

    // ------------------------------------------------------------------
    // Andamiaje del test
    // ------------------------------------------------------------------

    /**
     * Registra el controller de prueba como bean para que el
     * RequestMappingHandlerMapping real lo descubra. El advice no se registra
     * aca a proposito: que aparezca es justamente lo que se esta probando.
     */
    @TestConfiguration
    static class TestControllerConfig {

        @Bean
        WiringController wiringController() {
            return new WiringController();
        }
    }

    @RestController
    static class WiringController {

        @GetMapping("/wiring/not-found")
        void notFound() {
            throw new ResourceNotFoundException("Usuario", 7L);
        }

        @GetMapping("/wiring/min")
        void min(@RequestParam @Min(1) int page) {
        }

        @PostMapping("/wiring/body")
        void body(@Valid @RequestBody SampleRequest request) {
        }

        @GetMapping("/wiring/header")
        void header(@RequestHeader("X-Tenant") String tenant) {
        }

        @GetMapping("/wiring/typed/{id}")
        void typed(@PathVariable Long id) {
        }

        @GetMapping("/wiring/boom")
        void boom() {
            throw new IllegalStateException("Connection refused to jdbc:postgresql://localhost:5432/checkout");
        }

        /**
         * Lo que lanza Hibernate cuando el @Version de una fila no coincide, es
         * decir cuando dos operaciones simultaneas tocaron el mismo saldo.
         */
        @GetMapping("/wiring/concurrent")
        void concurrent() {
            throw new ObjectOptimisticLockingFailureException(TokenWallet.class, 1L);
        }

        record SampleRequest(
                @NotBlank(message = "El nombre es obligatorio.") String name,
                @Email(message = "El correo no tiene un formato valido.") String email) {
        }
    }

}
