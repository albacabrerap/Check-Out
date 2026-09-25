package com.checkout.backend.token_wallet.refresh_token.repository;

import com.checkout.backend.token_wallet.refresh_token.model.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
}
