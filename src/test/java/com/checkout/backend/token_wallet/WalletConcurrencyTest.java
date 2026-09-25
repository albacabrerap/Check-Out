package com.checkout.backend.token_wallet;

import com.checkout.backend.support.DatabaseCleaner;
import com.checkout.backend.token_wallet.repository.TokenWalletRepository;
import com.checkout.backend.token_wallet.service.TokenWalletService;
import com.checkout.backend.token_wallet.tktransaction.model.TokenReason;
import com.checkout.backend.token_wallet.tktransaction.repository.TokenTransactionRepository;
import com.checkout.backend.user.model.User;
import com.checkout.backend.user.repository.UserRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Doble gasto sobre el mismo monedero, con hilos de verdad.
 *
 * La auditoria dejo este hueco senalado: el analisis de concurrencia era de
 * codigo, no de ejecucion. Se razonaba que el @Version de TokenWallet impide la
 * perdida de actualizacion, pero nada lo comprobaba, y "esta anotado" no es lo
 * mismo que "funciona".
 *
 * El caso es el clasico: saldo 100, dos peticiones que quieren gastar 80 cada
 * una, simultaneas. Las dos leen el mismo saldo, las dos pasan la validacion de
 * "no queda negativo", y sin bloqueo optimista la segunda escritura pisaria a la
 * primera dejando el saldo en 20 despues de haber gastado 160.
 *
 * Lo que se afirma aqui no es cual de las dos gana, que depende del planificador y
 * seria un test inestable, sino la invariante: el saldo final tiene que cuadrar
 * exactamente con los movimientos que si se aplicaron, y nunca puede quedar
 * negativo. Esa es la propiedad que protege la economia del juego.
 */
@SpringBootTest
class WalletConcurrencyTest {

    private static final BigDecimal INITIAL = new BigDecimal("100.00");
    private static final BigDecimal SPEND = new BigDecimal("80.00");

    @Autowired private TokenWalletService walletService;
    @Autowired private UserRepository userRepository;
    @Autowired private TokenWalletRepository walletRepository;
    @Autowired private TokenTransactionRepository transactionRepository;
    @Autowired private JdbcTemplate jdbc;

    private User ana;

    @BeforeEach
    void setUp() {
        ana = userRepository.save(User.builder()
                .name("Ana")
                .email("ana@utec.edu.pe")
                .passwordHash("$2a$10$hash")
                .build());

        walletService.record(ana, INITIAL, TokenReason.ADJUSTMENT, null);
    }

    @AfterEach
    void cleanUp() {
        DatabaseCleaner.clean(jdbc);
    }

    @Test
    @DisplayName("Dos gastos simultaneos de 80 sobre un saldo de 100 no dejan el saldo en -60")
    void twoConcurrentSpendsCannotOverdrawTheWallet() throws Exception {
        // Una barrera para que los dos hilos entren a la vez de verdad. Sin ella
        // uno acabaria antes de que el otro empiece y el test no probaria nada.
        CyclicBarrier startTogether = new CyclicBarrier(2);
        ExecutorService pool = Executors.newFixedThreadPool(2);

        try {
            List<Callable<Outcome>> spends = List.of(
                    spendTask(startTogether), spendTask(startTogether));

            List<Future<Outcome>> futures = pool.invokeAll(spends);

            List<Outcome> outcomes = new ArrayList<>();
            for (Future<Outcome> future : futures) {
                outcomes.add(future.get());
            }

            long applied = outcomes.stream().filter(Outcome::succeeded).count();

            // Como mucho uno puede aplicarse: 80 + 80 = 160 no cabe en 100.
            assertThat(applied)
                    .as("dos gastos de 80 no pueden aplicarse los dos sobre un saldo de 100")
                    .isEqualTo(1);

            // El que no se aplico fallo por conflicto de concurrencia o por saldo
            // insuficiente, que son las dos formas correctas de perder esta carrera.
            // Lo que no vale es que se haya aplicado en silencio.
            BigDecimal expected = INITIAL.subtract(SPEND.multiply(BigDecimal.valueOf(applied)));

            BigDecimal balance = walletRepository.findByUserId(ana.getId())
                    .orElseThrow()
                    .getTokenBalance();

            assertThat(balance)
                    .as("el saldo tiene que cuadrar con los movimientos aplicados")
                    .isEqualByComparingTo(expected);
            assertThat(balance.signum()).as("el saldo nunca puede ser negativo").isNotNegative();

            // Y el libro tiene que contar lo mismo: el abono inicial mas los gastos
            // que de verdad se asentaron. Un asiento de un gasto que se deshizo
            // seria una desviacion entre el saldo y su historial.
            assertThat(transactionRepository.count()).isEqualTo(1 + applied);

        } finally {
            pool.shutdownNow();
        }
    }

    private Callable<Outcome> spendTask(CyclicBarrier startTogether) {
        return () -> {
            startTogether.await();
            try {
                walletService.record(ana, SPEND.negate(), TokenReason.MINIGAME, null);
                return new Outcome(true, null);
            } catch (OptimisticLockingFailureException conflict) {
                return new Outcome(false, conflict);
            } catch (RuntimeException insufficientOrOther) {
                return new Outcome(false, insufficientOrOther);
            }
        };
    }

    private record Outcome(boolean succeeded, RuntimeException failure) {
    }
}
