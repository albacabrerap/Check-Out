package com.checkout.backend.token_wallet.repository;

import com.checkout.backend.token_wallet.model.TokenWallet;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TokenWalletRepository extends JpaRepository<TokenWallet, Long> {
}