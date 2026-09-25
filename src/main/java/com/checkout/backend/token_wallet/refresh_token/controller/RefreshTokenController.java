package com.checkout.backend.token_wallet.refresh_token.controller;

import com.checkout.backend.token_wallet.refresh_token.repository.RefreshTokenRepository;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/refresh_token")
public class RefreshTokenController {
    private final RefreshTokenRepository refreshTokenRepository;

    public RefreshTokenController(RefreshTokenRepository refreshTokenRepository) {
        this.refreshTokenRepository = refreshTokenRepository;
    }

    // ...
}
