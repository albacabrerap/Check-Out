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


@RestController
@RequestMapping("/minigames")
public class MinigameController {

    private final MinigameService minigameService;

    public MinigameController(MinigameService minigameService) {
        this.minigameService = minigameService;
    }

    @GetMapping
    public ResponseEntity<List<MinigameResponse>> list(
            @RequestParam(defaultValue = "false") boolean includeUnpublished) {

        if (!includeUnpublished) {
            return ResponseEntity.ok(minigameService.listPublished());
        }
        return ResponseEntity.ok(minigameService.listAll());
    }

    /* GET /api/v1/minigames/{id} */
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

    /* PUT /api/v1/minigames/{id} */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<MinigameResponse> update(@PathVariable Long id,
                                                   @Valid @RequestBody MinigameRequest request) {
        return ResponseEntity.ok(minigameService.update(id, request));
    }

    /*DELETE /api/v1/minigames/{id}*/
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> archive(@PathVariable Long id) {
        minigameService.archive(id);
        return ResponseEntity.noContent().build();
    }
}