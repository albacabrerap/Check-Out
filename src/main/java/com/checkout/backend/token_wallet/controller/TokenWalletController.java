package com.checkout.backend.token_wallet.controller;

import com.checkout.backend.token_wallet.dto.TokenWalletResponse;
import com.checkout.backend.token_wallet.service.TokenWalletService;
import com.checkout.backend.token_wallet.tktransaction.dto.TokenTransactionResponse;
import com.checkout.backend.user.service.CurrentUserProvider;
import java.util.List;
import com.checkout.backend.web.PageResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Monedero de fichas del usuario autenticado. Solo lectura.
 *
 * La ausencia de POST, PUT y DELETE aqui no es una funcionalidad pendiente, es
 * la decision de seguridad central del modulo: si existiera un endpoint para
 * acreditarse fichas, el saldo dejaria de significar nada y con el los
 * minijuegos y la cartera simulada.
 *
 * Las fichas se mueven solo como consecuencia de un hecho — una partida jugada,
 * una orden ejecutada, una meta cumplida — y siempre desde el servicio que
 * gobierna ese hecho. El libro de movimientos deja ver exactamente eso: de donde
 * salio cada ficha.
 *
 * Como en /savings, la ruta no lleva id: un usuario tiene un solo monedero.
 */
@RestController
@RequestMapping("/token-wallet")
public class TokenWalletController {

    private final TokenWalletService walletService;
    private final CurrentUserProvider currentUser;

    public TokenWalletController(TokenWalletService walletService,
                                 CurrentUserProvider currentUser) {
        this.walletService = walletService;
        this.currentUser = currentUser;
    }

    /** GET /api/v1/token-wallet */
    @GetMapping
    public ResponseEntity<TokenWalletResponse> getWallet() {
        return ResponseEntity.ok(walletService.getSummary(currentUser.requireCurrentUser()));
    }

    /**
     * GET /api/v1/token-wallet/transactions
     *
     * Cada movimiento trae balanceAfter, el saldo que quedo tras aplicarlo, de
     * modo que el historial se puede auditar sin recalcular toda la suma.
     */
    @GetMapping("/transactions")
    public ResponseEntity<PageResponse<TokenTransactionResponse>> listTransactions(
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(
                walletService.listTransactions(currentUser.requireCurrentUser(), pageable));
    }

}
