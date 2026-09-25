package com.checkout.backend.token_wallet.service;

import com.checkout.backend.exceptions.InvalidRequestException;
import com.checkout.backend.token_wallet.dto.TokenWalletResponse;
import com.checkout.backend.token_wallet.model.TokenWallet;
import com.checkout.backend.token_wallet.repository.TokenWalletRepository;
import com.checkout.backend.token_wallet.tktransaction.dto.TokenTransactionResponse;
import com.checkout.backend.token_wallet.tktransaction.model.TokenReason;
import com.checkout.backend.token_wallet.tktransaction.model.TokenTransaction;
import com.checkout.backend.token_wallet.tktransaction.repository.TokenTransactionRepository;
import com.checkout.backend.user.model.User;
import com.checkout.backend.web.PageResponse;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.modelmapper.ModelMapper;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Monedero de fichas y su libro de movimientos.
 *
 * Aqui hay una decision de seguridad que conviene dejar explicita: la API no
 * expone ninguna forma de modificar el saldo. No hay endpoint que acredite ni
 * descuente fichas, y por eso este servicio no recibe ningun Request: las fichas
 * solo se mueven como consecuencia de algo que ocurrio — una partida jugada, una
 * orden ejecutada, una meta cumplida — y siempre desde el servicio que gobierna
 * ese hecho.
 *
 * La razon es simple: si un usuario pudiera llamar a un endpoint para sumarse
 * fichas, el saldo dejaria de significar nada, y con el los minijuegos y la
 * cartera simulada. Que en el paquete de DTOs no exista ningun TokenWalletRequest
 * ni TokenTransactionRequest confirma que asi estaba pensado desde el modelo.
 *
 * Cada movimiento guarda el saldo resultante en balanceAfter, de modo que el
 * libro se puede auditar en cualquier punto y una desviacion se detecta leyendo
 * una sola fila en vez de sumando todo el historial.
 */
@Service
public class TokenWalletService {

    private final TokenWalletRepository walletRepository;
    private final TokenTransactionRepository transactionRepository;
    private final ModelMapper mapper;

    public TokenWalletService(TokenWalletRepository walletRepository,
                              TokenTransactionRepository transactionRepository,
                              ModelMapper mapper) {
        this.walletRepository = walletRepository;
        this.transactionRepository = transactionRepository;
        this.mapper = mapper;
    }

    /**
     * El monedero del usuario, creandolo en cero la primera vez.
     *
     * El UNIQUE sobre user_id impide que dos peticiones simultaneas dejen dos
     * monederos, que seria la forma mas directa de duplicar el saldo.
     */
    /**
     * El monedero del usuario, creandolo en cero la primera vez.
     *
     * El UNIQUE sobre user_id impide que dos peticiones simultaneas dejen dos
     * monederos, que seria la forma mas directa de duplicar el saldo.
     *
     * Queda una carrera menor: dos peticiones del mismo usuario que lleguen a la
     * vez pueden ver las dos que no existe, y la que pierde recibe un 409 en vez de
     * su monedero. No se parchea aqui con un catch y un reintento porque la
     * insercion tendria que ir en su propia transaccion, y llamar a un metodo
     * @Transactional del propio bean no pasa por el proxy: la anotacion no haria
     * nada y quedaria un arreglo que parece funcionar y no funciona.
     *
     * Se resuelve en el origen: AuthService crea monedero, ahorro y cartera al
     * registrar, dentro de esa misma transaccion. Asi para un usuario registrado
     * por la aplicacion este metodo nunca inserta, y la carrera no tiene ventana.
     */
    @Transactional
    public TokenWallet getOrCreate(User user) {
        return walletRepository.findByUserId(user.getId())
                .orElseGet(() -> walletRepository.save(
                        TokenWallet.builder()
                                .user(user)
                                .tokenBalance(BigDecimal.ZERO)
                                .build()));
    }

    @Transactional
    public TokenWalletResponse getSummary(User user) {
        return mapper.map(getOrCreate(user), TokenWalletResponse.class);
    }

    @Transactional
    public PageResponse<TokenTransactionResponse> listTransactions(User user, Pageable pageable) {
        return PageResponse.of(transactionRepository
                .findByTokenWalletIdOrderByCreatedAtDesc(getOrCreate(user).getId(), pageable)
                .map(transaction -> mapper.map(transaction, TokenTransactionResponse.class)));
    }

    /**
     * Comprueba si el usuario puede gastar un importe, sin tocar nada.
     *
     * Existe para que quien tenga que rechazar una operacion pueda decidirlo
     * *antes* de entrar a escribir, en vez de llamar a record y atrapar su
     * excepcion. La diferencia no es de estilo: una excepcion que escapa de un
     * metodo @Transactional marca la transaccion como rollback-only aunque
     * quien llama la atrape, y el commit posterior falla con
     * UnexpectedRollbackException. Preguntar primero evita ese camino entero.
     *
     * No usa getOrCreate a proposito: es readOnly y no debe crear el monedero
     * como efecto secundario de una consulta. Un usuario sin monedero tiene
     * saldo cero, que es la respuesta correcta.
     *
     * @return el motivo del rechazo, o vacio si el gasto es posible
     */
    @Transactional(readOnly = true)
    public Optional<String> reasonToRejectSpending(User user, BigDecimal amount) {
        BigDecimal balance = walletRepository.findByUserId(user.getId())
                .map(TokenWallet::getTokenBalance)
                .orElse(BigDecimal.ZERO);

        if (balance.compareTo(amount) < 0) {
            return Optional.of("No tienes fichas suficientes. Saldo actual: " + balance
                    + ", se necesitan " + amount + ".");
        }
        return Optional.empty();
    }

    /**
     * Asienta un movimiento de fichas.
     *
     * Solo lo llaman otros servicios del backend, nunca un controller. El signo
     * del importe decide la direccion: positivo acredita, negativo descuenta.
     *
     * Es idempotente por el par (motivo, referencia), que tiene UNIQUE en la
     * tabla. Si la misma partida o la misma orden intenta cobrarse dos veces, la
     * segunda no hace nada y devuelve el movimiento original. Sin eso, un
     * reintento tras un error de red podria pagar la misma recompensa dos veces.
     *
     * @param amount      importe con signo; no puede ser cero, porque un
     *                    movimiento que no mueve nada solo ensucia el libro y la
     *                    base lo rechaza con un CHECK
     * @param referenceId id de la operacion que lo origina, para poder rastrear
     *                    cada ficha hasta su causa
     */
    @Transactional
    public TokenTransaction record(User user, BigDecimal amount, TokenReason reason,
                                   Long referenceId) {
        if (amount.signum() == 0) {
            throw new InvalidRequestException("Un movimiento de fichas no puede ser de cero.");
        }

        if (referenceId != null) {
            var existing = transactionRepository.findByReasonAndReferenceId(reason, referenceId);
            if (existing.isPresent()) {
                return existing.get();
            }
        }

        TokenWallet wallet = getOrCreate(user);
        BigDecimal balance = wallet.getTokenBalance().add(amount);

        // La entidad ya declara @DecimalMin("0"), pero comprobarlo aqui permite
        // responder un 400 con un mensaje util en vez de dejar que la validacion
        // salte al guardar con un texto que no dice cuanto falta.
        if (balance.signum() < 0) {
            throw new InvalidRequestException(
                    "No tienes fichas suficientes. Saldo actual: " + wallet.getTokenBalance()
                            + ", se necesitan " + amount.abs() + ".");
        }

        wallet.setTokenBalance(balance);

        return transactionRepository.save(TokenTransaction.builder()
                .tokenWallet(wallet)
                .amount(amount)
                .reason(reason)
                .referenceId(referenceId)
                .balanceAfter(balance)
                .build());
    }

}
