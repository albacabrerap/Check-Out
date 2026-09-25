package com.checkout.backend.token_wallet.tktransaction.controller;

import com.checkout.backend.token_wallet.tktransaction.repository.TokenTransactionRepository;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/tktransaction")
public class TokenTransactionController {
    private final TokenTransactionRepository tokenTransactionRepository;

    public TokenTransactionController(TokenTransactionRepository tokenTransactionRepository) {
        this.tokenTransactionRepository = tokenTransactionRepository;
    }

    // ...
}
