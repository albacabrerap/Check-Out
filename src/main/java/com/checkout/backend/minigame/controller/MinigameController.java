package com.checkout.backend.minigame.controller;

import com.checkout.backend.minigame.dto.MinigameRequest;
import com.checkout.backend.minigame.dto.MinigameResponse;
import com.checkout.backend.minigame.service.MinigameService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * Catalogo de minijuegos.
 *
 * Es contenido comun, no de un usuario, asi que el modelo de permisos cambia
 * respecto al resto de la API: leer lo puede hacer cualquier autenticado,
 * escribir es de ADMIN. `tokenCost` y `maxTokenReward` son los parametros de la
 * economia de fichas, y quien pueda editarlos puede crearse un juego que cueste
 * cero y pague mil.
 *
 * El listado publico solo devuelve los PUBLISHED. Los borradores y los
 * archivados se consultan en /minigames/all, que es de administracion.
 */
@RestController
@RequestMapping("/minigames")
public class MinigameController {

    private final MinigameService minigameService;

    public MinigameController(MinigameService minigameService) {
        this.minigameService = minigameService;
    }

    /**
     * GET /api/v1/minigames — el catalogo jugable
     *
     * Con ?includeUnpublished=true incluye borradores y archivados, y eso exige
     * ADMIN. El permiso lo comprueba el servicio con su propio @PreAuthorize, que
     * protege la operacion aunque se la llame desde otro sitio; aqui no se puede
     * anotar el metodo porque lo llaman los dos tipos de usuario.
     */
    @GetMapping
    public ResponseEntity<List<MinigameResponse>> list(
            @RequestParam(defaultValue = "false") boolean includeUnpublished) {

        if (!includeUnpublished) {
            return ResponseEntity.ok(minigameService.listPublished());
        }
        return ResponseEntity.ok(minigameService.listAll());
    }

    /** GET /api/v1/minigames/{id} */
    @GetMapping("/{id}")
    public ResponseEntity<MinigameResponse> get(@PathVariable Long id) {
        return ResponseEntity.ok(minigameService.getPublished(id));
    }

    /** POST /api/v1/minigames */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<MinigameResponse> create(@Valid @RequestBody MinigameRequest request) {
        MinigameResponse created = minigameService.create(request);

        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(created.getId())
                .toUri();

        return ResponseEntity.created(location).body(created);
    }

    /** PUT /api/v1/minigames/{id} */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<MinigameResponse> update(@PathVariable Long id,
                                                   @Valid @RequestBody MinigameRequest request) {
        return ResponseEntity.ok(minigameService.update(id, request));
    }

    /**
     * DELETE /api/v1/minigames/{id}
     *
     * Archiva en vez de borrar: las partidas jugadas referencian esta fila y
     * borrarla destruiria el historial de los usuarios.
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> archive(@PathVariable Long id) {
        minigameService.archive(id);
        return ResponseEntity.noContent().build();
    }

}
