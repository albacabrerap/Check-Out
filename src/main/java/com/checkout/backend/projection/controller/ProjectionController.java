package com.checkout.backend.projection.controller;

import com.checkout.backend.projection.repository.ProjectionRepository;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/projection")
public class ProjectionController {
    private final ProjectionRepository projectionRepository;

    public ProjectionController(ProjectionRepository projectionRepository) {
        this.projectionRepository = projectionRepository;
    }

    // ...
}
