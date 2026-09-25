package com.checkout.backend.minigame.controller;

import com.checkout.backend.minigame.repository.MinigameRepository;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/minigame")
public class MinigameController {
    private final MinigameRepository minigameRepository;

    public MinigameController(MinigameRepository minigameRepository) {
        this.minigameRepository = minigameRepository;
    }

    // ...
}
