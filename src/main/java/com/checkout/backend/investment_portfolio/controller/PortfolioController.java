package com.checkout.backend.investment_portfolio.controller;

import com.checkout.backend.investment_portfolio.dto.PortfolioResponse;
import com.checkout.backend.investment_portfolio.position.dto.PositionResponse;
import com.checkout.backend.investment_portfolio.service.PortfolioService;
import com.checkout.backend.user.service.CurrentUserProvider;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// Sim user: lecture, balance -> user has one balance total
@RestController
@RequestMapping("/portfolio")
public class PortfolioController {
    private final PortfolioService portfolioService;
    private final CurrentUserProvider currentUser;

    public PortfolioController(PortfolioService portfolioService,
                               CurrentUserProvider currentUser) {
        this.portfolioService = portfolioService;
        this.currentUser = currentUser;
    }

    // GET /api/v1/portfolio
    @GetMapping
    public ResponseEntity<PortfolioResponse> getPortfolio() {
        return ResponseEntity.ok(portfolioService.getSummary(currentUser.requireCurrentUser()));
    }

    // GET /api/v1/portfolio/positions
    @GetMapping("/positions")
    public ResponseEntity<List<PositionResponse>> listPositions() {
        return ResponseEntity.ok(portfolioService.listPositions(currentUser.requireCurrentUser()));
    }
}