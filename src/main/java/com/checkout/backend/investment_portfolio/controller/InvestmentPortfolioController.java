package com.checkout.backend.investment_portfolio.controller;

import com.checkout.backend.investment_portfolio.repository.InvestmentPortafolioRepository;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/investment_portfolio")
public class InvestmentPortfolioController {
    private final InvestmentPortafolioRepository investmentPortafolioRepository;

    public InvestmentPortfolioController(InvestmentPortafolioRepository investmentPortafolioRepository) {
        this.investmentPortafolioRepository = investmentPortafolioRepository;
    }

    // ...
}
