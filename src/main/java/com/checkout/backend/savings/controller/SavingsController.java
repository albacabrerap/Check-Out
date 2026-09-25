package com.checkout.backend.savings.controller;

import com.checkout.backend.savings.repository.SavingsRepository;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/savings")
public class SavingsController {
    private final SavingsRepository savingsRepository;

    public SavingsController(SavingsRepository savingsRepository) {
        this.savingsRepository = savingsRepository;
    }

    // ...
}
