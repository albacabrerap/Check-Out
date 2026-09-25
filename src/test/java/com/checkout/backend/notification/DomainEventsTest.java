package com.checkout.backend.notification;

import com.checkout.backend.email.EmailDetails;
import com.checkout.backend.email.EmailService;
import com.checkout.backend.savings.goal.contribution.dto.ContributionRequest;
import com.checkout.backend.savings.goal.contribution.model.ContributionSource;
import com.checkout.backend.savings.goal.contribution.service.ContributionService;
import com.checkout.backend.savings.goal.dto.SavingsGoalRequest;
import com.checkout.backend.savings.goal.model.GoalStatus;
import com.checkout.backend.savings.goal.service.SavingsGoalService;
import com.checkout.backend.savings.income.dto.IncomeRequest;
import com.checkout.backend.savings.income.service.IncomeService;
import com.checkout.backend.support.DatabaseCleaner;
import com.checkout.backend.token_wallet.service.TokenWalletService;
import com.checkout.backend.user.model.User;
import com.checkout.backend.user.repository.UserRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Los eventos de dominio, de punta a punta.
 *
 * Cumplir una meta acredita fichas y avisa por correo, y las dos mitades estan
 * separadas a proposito: las fichas dentro de la transaccion que marca la meta
 * como cumplida, el aviso despues del commit.
 *
 * Esta clase NO lleva @Transactional, y aqui no es solo una buena practica: es
 * imprescindible. Un @TransactionalEventListener con phase AFTER_COMMIT solo se
 * dispara cuando hay un commit de verdad. Envuelto en la transaccion del test, que
 * siempre acaba en rollback, el listener no correria nunca y el test pasaria sin
 * probar nada.
 */
@SpringBootTest
class DomainEventsTest {

    @Autowired private IncomeService incomeService;
    @Autowired private SavingsGoalService goalService;
    @Autowired private ContributionService contributionService;
    @Autowired private TokenWalletService walletService;
    @Autowired private UserRepository userRepository;
    @Autowired private JdbcTemplate jdbc;

    @MockitoBean private EmailService emailService;

    private User ana;

    @BeforeEach
    void setUp() {
        when(emailService.send(any())).thenReturn(CompletableFuture.completedFuture(null));

        ana = userRepository.save(User.builder()
                .name("Ana")
                .email("ana@utec.edu.pe")
                .passwordHash("$2a$10$hash")
                .build());
    }

    @AfterEach
    void cleanUp() {
        DatabaseCleaner.clean(jdbc);
    }

    @Test
    @DisplayName("Cumplir una meta acredita la recompensa y avisa por correo")
    void completingAGoalRewardsAndNotifies() {
        income("5000.00");
        Long goalId = goal("Viaje a Cusco", "5000.00");

        // Los dos primeros aportes no completan la meta: no hay recompensa ni aviso.
        contribute(goalId, "1000.00");
        contribute(goalId, "2000.00");

        assertThat(goalService.get(ana, goalId).getStatus()).isEqualTo(GoalStatus.IN_PROGRESS);
        verify(emailService, never()).send(any());
        assertThat(walletService.getSummary(ana).getTokenBalance()).isEqualByComparingTo("0.00");

        // El tercero la cierra exactamente en 5000.
        contribute(goalId, "2000.00");

        assertThat(goalService.get(ana, goalId).getStatus()).isEqualTo(GoalStatus.COMPLETED);

        // Recompensa: 1% de 5000 = 50.00, por debajo del tope de 100.
        assertThat(walletService.getSummary(ana).getTokenBalance()).isEqualByComparingTo("50.00");

        // Y el aviso salio, con el destinatario resuelto en el servidor.
        ArgumentCaptor<EmailDetails> sent = ArgumentCaptor.forClass(EmailDetails.class);
        verify(emailService, timeout(5000)).send(sent.capture());

        assertThat(sent.getValue().recipient()).isEqualTo("ana@utec.edu.pe");
        assertThat(sent.getValue().subject()).contains("Viaje a Cusco");
        assertThat(sent.getValue().body()).contains("5000.00").contains("50.00");
    }

    @Test
    @DisplayName("La recompensa por una meta no se paga dos veces")
    void theRewardIsNotPaidTwice() {
        income("200.00");
        Long goalId = goal("Audifonos", "200.00");
        contribute(goalId, "200.00");

        BigDecimal afterCompletion = walletService.getSummary(ana).getTokenBalance();
        assertThat(afterCompletion).isEqualByComparingTo("2.00");

        // Aportar a una meta ya cumplida se rechaza, asi que no hay segundo pago.
        // El UNIQUE(reason, reference_id) del libro es la red por debajo.
        assertThatContributionIsRejected(goalId, "1.00");

        assertThat(walletService.getSummary(ana).getTokenBalance())
                .isEqualByComparingTo(afterCompletion);
    }

    @Test
    @DisplayName("Un fallo de correo no tumba la operacion que ya se guardo")
    void aMailFailureDoesNotBreakTheOperation() {
        when(emailService.send(any())).thenThrow(new RuntimeException("SMTP caido"));

        income("300.00");
        Long goalId = goal("Libros", "300.00");

        // No lanza: la meta se cumple igual.
        contribute(goalId, "300.00");

        assertThat(goalService.get(ana, goalId).getStatus()).isEqualTo(GoalStatus.COMPLETED);
        assertThat(walletService.getSummary(ana).getTokenBalance()).isEqualByComparingTo("3.00");
    }

    // ------------------------------------------------------------------
    // Ayudas
    // ------------------------------------------------------------------

    private void income(String amount) {
        IncomeRequest request = new IncomeRequest();
        request.setAmount(new BigDecimal(amount));
        request.setSource("Sueldo");
        request.setDate(LocalDate.now());
        incomeService.create(ana, request);
    }

    private Long goal(String name, String target) {
        SavingsGoalRequest request = new SavingsGoalRequest();
        request.setName(name);
        request.setTargetAmount(new BigDecimal(target));
        request.setDeadline(LocalDate.now().plusMonths(6));
        return goalService.create(ana, request).getId();
    }

    private void contribute(Long goalId, String amount) {
        ContributionRequest request = new ContributionRequest();
        request.setAmount(new BigDecimal(amount));
        request.setSource(ContributionSource.MANUAL);
        contributionService.create(ana, goalId, request);
    }

    private void assertThatContributionIsRejected(Long goalId, String amount) {
        try {
            contribute(goalId, amount);
            throw new AssertionError("se esperaba un rechazo al aportar a una meta cumplida");
        } catch (RuntimeException expected) {
            assertThat(expected.getMessage()).contains("cumplida");
        }
    }
}
