package com.checkout.backend.token_wallet.tktransaction.repository;

import com.checkout.backend.token_wallet.tktransaction.model.TokenTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TokenTransactionRepository extends JpaRepository<TokenTransaction, Long> {
}
