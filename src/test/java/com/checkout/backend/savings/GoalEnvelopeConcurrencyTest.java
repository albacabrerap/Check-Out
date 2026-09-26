package com.checkout.backend.savings;

import com.checkout.backend.savings.goal.contribution.dto.ContributionRequest;
import com.checkout.backend.savings.goal.contribution.model.ContributionSource;
import com.checkout.backend.savings.goal.contribution.service.ContributionService;
import com.checkout.backend.savings.goal.model.SavingsGoal;
import com.checkout.backend.savings.goal.repository.SavingsGoalRepository;
import com.checkout.backend.savings.service.SavingsService;
import com.checkout.backend.support.DatabaseCleaner;
import com.checkout.backend.user.model.User;
import com.checkout.backend.user.repository.UserRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
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
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Dos aportes simultaneos a metas distintas sobre el mismo saldo.
 *
 * Es la carrera que el {@code @Version} de la meta no podia cubrir, y la razon
 * es geometrica: dos aportes a la MISMA meta compiten por una fila y el
 * versionado los ordena, pero dos aportes a metas DISTINTAS escriben filas
 * distintas y no se cruzan nunca. Los dos leian el mismo disponible, los dos lo
 * validaban contra el saldo de antes, y los dos escribian. Con 1.000 de saldo
 * libre se podian comprometer 1.600.
 *
 * El dinero no desaparece —los soles siguen en savings— pero el modelo del sobre
 * virtual queda roto: el disponible pasa a ser negativo y el usuario no puede
 * gastar ni cumplir lo que ya prometio.
 *
 * Como en el test del monedero, lo que se afirma no es cual de los dos gana,
 * que depende del planificador, sino la invariante: lo comprometido nunca puede
 * superar lo que hay.
 */
@SpringBootTest
class GoalEnvelopeConcurrencyTest {

    private static final BigDecimal BALANCE = new BigDecimal("1000.00");
    private static final BigDecimal CONTRIBUTION = new BigDecimal("800.00");

    @Autowired private ContributionService contributionService;
    @Autowired private SavingsService savingsService;
    @Autowired private SavingsGoalRepository goalRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private JdbcTemplate jdbc;

    private User ana;
    private Long laptopId;
    private Long viajeId;

    @BeforeEach
    void setUp() {
        ana = userRepository.save(User.builder()
                .name("Ana")
                .email("ana@utec.edu.pe")
                .passwordHash("$2a$10$hash")
                .build());

        savingsService.credit(ana, BALANCE);

        laptopId = newGoal("Laptop").getId();
        viajeId = newGoal("Viaje").getId();
    }

    @AfterEach
    void cleanUp() {
        DatabaseCleaner.clean(jdbc);
    }

    @Test
    @DisplayName("Dos aportes simultaneos de 800 sobre 1000 no comprometen 1600")
    void twoConcurrentContributionsCannotOvercommitTheBalance() throws Exception {
        CyclicBarrier startTogether = new CyclicBarrier(2);
        ExecutorService pool = Executors.newFixedThreadPool(2);

        try {
            List<Future<Outcome>> futures = pool.invokeAll(List.of(
                    contributeTask(startTogether, laptopId),
                    contributeTask(startTogether, viajeId)));

            List<Outcome> outcomes = new ArrayList<>();
            for (Future<Outcome> future : futures) {
                outcomes.add(future.get());
            }

            long applied = outcomes.stream().filter(Outcome::succeeded).count();

            assertThat(applied)
                    .as("800 + 800 no caben en un saldo de 1000")
                    .isEqualTo(1);

            BigDecimal committed = savingsService.committedAmount(ana);
            assertThat(committed)
                    .as("lo comprometido tiene que cuadrar con los aportes aplicados")
                    .isEqualByComparingTo(CONTRIBUTION.multiply(BigDecimal.valueOf(applied)));

            assertThat(savingsService.availableBalance(ana).signum())
                    .as("el disponible nunca puede quedar negativo")
                    .isNotNegative();
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("Secuencialmente, el segundo aporte se rechaza por saldo disponible")
    void theSecondContributionIsRejectedOnceTheFirstCommitted() {
        contributionService.create(ana, laptopId, request());

        assertThat(savingsService.availableBalance(ana))
                .as("tras comprometer 800 de 1000 quedan 200")
                .isEqualByComparingTo("200.00");

        Outcome second = run(() -> contributionService.create(ana, viajeId, request()));

        assertThat(second.succeeded())
                .as("no se puede comprometer 800 cuando solo quedan 200")
                .isFalse();
    }

    // ------------------------------------------------------------------

    private SavingsGoal newGoal(String name) {
        return goalRepository.save(SavingsGoal.builder()
                .user(ana)
                .name(name)
                .targetAmount(new BigDecimal("900.00"))
                .deadline(LocalDate.now().plusMonths(6))
                .build());
    }

    private ContributionRequest request() {
        return ContributionRequest.builder()
                .amount(CONTRIBUTION)
                .source(ContributionSource.MANUAL)
                .build();
    }

    private Callable<Outcome> contributeTask(CyclicBarrier startTogether, Long goalId) {
        return () -> {
            startTogether.await();
            return run(() -> contributionService.create(ana, goalId, request()));
        };
    }

    /**
     * Perder esta carrera es correcto de varias formas: el rechazo de dominio por
     * saldo, un conflicto de bloqueo optimista o un tiempo de espera del bloqueo
     * pesimista. Lo que no vale es aplicarse en silencio, y eso es lo que mide el
     * conteo de exitos.
     */
    private Outcome run(Runnable action) {
        try {
            action.run();
            return new Outcome(true, null);
        } catch (RuntimeException rejected) {
            return new Outcome(false, rejected);
        }
    }

    private record Outcome(boolean succeeded, RuntimeException failure) {
    }
}
