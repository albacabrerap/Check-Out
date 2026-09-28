package com.checkout.backend.web;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * Estado publico de la API.
 *
 * Responde en "/" para que abrir la IP del servidor en el navegador muestre que
 * la API esta viva y adonde ir, en vez de un 401. Tambien en /api/v1/health
 * para quien quiera comprobarlo desde un cliente o un monitor.
 *
 * Es @Controller + @ResponseBody y no @RestController a proposito: el prefijo
 * /api/v1 de ApiVersioningConfig solo se aplica a los @RestController, y "/"
 * tiene que quedarse en la raiz. Las dos rutas se abren en SecurityConfig.
 */
@Controller
public class StatusController {

    @GetMapping({"/", "/api/v1/health"})
    @ResponseBody
    public Map<String, Object> status() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("service", "Check-Out API");
        body.put("status", "UP");
        body.put("docs", "/swagger-ui/index.html");
        body.put("register", "POST /api/v1/auth/register");
        body.put("login", "POST /api/v1/auth/login");
        body.put("auth", "Las demas rutas requieren el header Authorization: Bearer <accessToken>");
        return body;
    }
}
