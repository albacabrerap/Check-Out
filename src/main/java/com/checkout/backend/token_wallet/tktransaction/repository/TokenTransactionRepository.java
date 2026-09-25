package com.checkout.backend.token_wallet.tktransaction.repository;

import com.checkout.backend.token_wallet.tktransaction.model.TokenReason;
import com.checkout.backend.token_wallet.tktransaction.model.TokenTransaction;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Acceso al libro de movimientos de fichas.
 */
public interface TokenTransactionRepository extends JpaRepository<TokenTransaction, Long> {

    Page<TokenTransaction> findByTokenWalletIdOrderByCreatedAtDesc(
            Long walletId, Pageable pageable);

    /**
     * Busca un movimiento por el par (motivo, referencia), que tiene UNIQUE en la
     * tabla. Es lo que impide cobrar dos veces la misma operacion: antes de
     * asentar un movimiento originado por una partida o una orden, se comprueba
     * si esa operacion ya se asento.
     */
    Optional<TokenTransaction> findByReasonAndReferenceId(TokenReason reason, Long referenceId);
}
