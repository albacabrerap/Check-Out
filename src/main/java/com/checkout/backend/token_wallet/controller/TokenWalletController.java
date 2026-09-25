package com.checkout.backend.token_wallet.controller;

import com.checkout.backend.token_wallet.repository.TokenWalletRepository;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/token_wallet")
public class TokenWalletController {
    private final TokenWalletRepository tokenWalletRepository;

    public TokenWalletController(TokenWalletRepository tokenWalletRepository) {
        this.tokenWalletRepository = tokenWalletRepository;
    }

    // ...
}
