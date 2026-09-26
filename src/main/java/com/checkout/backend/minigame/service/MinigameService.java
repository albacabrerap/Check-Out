package com.checkout.backend.minigame.service;

import com.checkout.backend.exceptions.DuplicateResourceException;
import com.checkout.backend.exceptions.ResourceNotFoundException;
import com.checkout.backend.minigame.dto.MinigameRequest;
import com.checkout.backend.minigame.dto.MinigameResponse;
import com.checkout.backend.minigame.model.Minigame;
import com.checkout.backend.minigame.model.MinigameStatus;
import com.checkout.backend.minigame.repository.MinigameRepository;
import java.util.List;
import org.modelmapper.ModelMapper;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
public class MinigameService {

    private final MinigameRepository minigameRepository;
    private final ModelMapper mapper;

    public MinigameService(MinigameRepository minigameRepository, ModelMapper mapper) {
        this.minigameRepository = minigameRepository;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public List<MinigameResponse> listPublished() {
        return minigameRepository.findByStatusOrderByTitleAsc(MinigameStatus.PUBLISHED)
                .stream()
                .map(minigame -> mapper.map(minigame, MinigameResponse.class))
                .toList();
    }

    /** Catalogo completo, incluidos borradores y archivados. Solo administracion. */
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional(readOnly = true)
    public List<MinigameResponse> listAll() {
        return minigameRepository.findAll().stream()
                .map(minigame -> mapper.map(minigame, MinigameResponse.class))
                .toList();
    }

    @Transactional(readOnly = true)
    public MinigameResponse getPublished(Long id) {
        return mapper.map(findPublished(id), MinigameResponse.class);
    }

    /**
     * Resuelve un minijuego jugable.
     *
     * Devuelve 404 y no 403 cuando existe pero no esta publicado: para un jugador
     * un borrador no deberia ser distinguible de algo que no existe.
     */
    @Transactional(readOnly = true)
    public Minigame findPublished(Long id) {
        return minigameRepository.findByIdAndStatus(id, MinigameStatus.PUBLISHED)
                .orElseThrow(() -> new ResourceNotFoundException("Minijuego", id));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public MinigameResponse create(MinigameRequest request) {
        if (minigameRepository.existsByTitleIgnoreCase(request.getTitle())) {
            throw new DuplicateResourceException(
                    "Ya existe un minijuego titulado '" + request.getTitle() + "'.");
        }

        Minigame minigame = Minigame.builder()
                .title(request.getTitle())
                .type(request.getType())
                .topic(request.getTopic())
                .tokenCost(request.getTokenCost())
                .maxTokenReward(request.getMaxTokenReward())
                .status(request.getStatus())
                .build();

        return mapper.map(minigameRepository.save(minigame), MinigameResponse.class);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public MinigameResponse update(Long id, MinigameRequest request) {
        Minigame minigame = minigameRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Minijuego", id));

        if (!minigame.getTitle().equalsIgnoreCase(request.getTitle())
                && minigameRepository.existsByTitleIgnoreCase(request.getTitle())) {
            throw new DuplicateResourceException(
                    "Ya existe un minijuego titulado '" + request.getTitle() + "'.");
        }

        minigame.setTitle(request.getTitle());
        minigame.setType(request.getType());
        minigame.setTopic(request.getTopic());
        minigame.setTokenCost(request.getTokenCost());
        minigame.setMaxTokenReward(request.getMaxTokenReward());
        minigame.setStatus(request.getStatus());

        return mapper.map(minigame, MinigameResponse.class);
    }

    /**
     * Retira un minijuego del catalogo archivandolo.
     *
     * No se borra la fila: las partidas jugadas la referencian, y borrarla
     * destruiria el historial de los usuarios o rompiria la clave foranea.
     * Archivar lo saca del catalogo y conserva lo ocurrido, que es lo mismo que
     * hace la baja de usuario en este proyecto.
     */
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public void archive(Long id) {
        Minigame minigame = minigameRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Minijuego", id));
        minigame.setStatus(MinigameStatus.ARCHIVED);
    }

}
