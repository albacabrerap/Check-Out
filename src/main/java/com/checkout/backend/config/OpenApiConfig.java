package com.checkout.backend.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

/**
 * Declara en la documentacion OpenAPI que la API usa JWT como Bearer token.
 *
 * Sin esto Swagger UI no muestra el boton "Authorize", asi que no hay forma de
 * mandar el header Authorization desde la pagina y todo endpoint protegido
 * responde 401. Solo afecta a la documentacion: la seguridad real sigue en
 * SecurityConfig. Los endpoints publicos (/auth/**) funcionan igual con o sin
 * token.
 */
@Configuration
@OpenAPIDefinition(
        info = @Info(title = "Check-Out API", version = "v1"),
        security = @SecurityRequirement(name = "bearerAuth"))
@SecurityScheme(
        name = "bearerAuth",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT")
public class OpenApiConfig {
}
