package com.checkout.backend.investment_portfolio;

import com.checkout.backend.investment_portfolio.asset.model.Asset;
import com.checkout.backend.investment_portfolio.asset.model.AssetType;
import com.checkout.backend.investment_portfolio.asset.quote.model.AssetQuote;
import com.checkout.backend.investment_portfolio.asset.quote.repository.AssetQuoteRepository;
import com.checkout.backend.investment_portfolio.asset.repository.AssetRepository;
import com.checkout.backend.investment_portfolio.dto.PortfolioResponse;
import com.checkout.backend.investment_portfolio.position.dto.PositionResponse;
import com.checkout.backend.investment_portfolio.service.PortfolioService;
import com.checkout.backend.investment_portfolio.trade_order.dto.TradeOrderRequest;
import com.checkout.backend.investment_portfolio.trade_order.dto.TradeOrderResponse;
import com.checkout.backend.investment_portfolio.trade_order.model.OrderSide;
import com.checkout.backend.investment_portfolio.trade_order.model.OrderStatus;
import com.checkout.backend.investment_portfolio.trade_order.service.TradeOrderService;
import com.checkout.backend.support.DatabaseCleaner;
import com.checkout.backend.token_wallet.service.TokenWalletService;
import com.checkout.backend.token_wallet.tktransaction.model.TokenReason;
import com.checkout.backend.user.model.User;
import com.checkout.backend.user.repository.UserRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Coste medio ponderado, PnL realizado y PnL no realizado, con numeros a mano.
 *
 * Esta clase existe porque la auditoria encontro que el calculo financiero mas
 * delicado del proyecto no tenia ni un test: el coste medio se comprobaba de
 * refilon en un flujo de la API, y el PnL no se comprobaba en absoluto. Una
 * formula de dinero sin test es una formula que nadie puede cambiar con
 * confianza.
 *
 * Los casos son los de la auditoria, con los valores calculados a mano fuera del
 * codigo. Eso es lo que hace que el test sirva: si se comprobara contra lo que el
 * propio metodo devuelve, pasaria igual estando mal.
 *
 * Va contra los servicios y no contra HTTP a proposito: lo que se verifica aqui
 * es aritmetica, y la capa REST ya esta cubierta en otro sitio. Sin
 * @Transactional, por la misma razon que en los demas flujos de escritura.
 */
@SpringBootTest
class PortfolioPnlTest {

    @Autowired private TradeOrderService orderService;
    @Autowired private PortfolioService portfolioService;
    @Autowired private TokenWalletService walletService;
    @Autowired private UserRepository userRepository;
    @Autowired private AssetRepository assetRepository;
    @Autowired private AssetQuoteRepository quoteRepository;
    @Autowired private JdbcTemplate jdbc;

    private User ana;
    private Asset voo;

    @BeforeEach
    void setUp() {
        ana = userRepository.save(User.builder()
                .name("Ana")
                .email("ana@utec.edu.pe")
                .passwordHash("$2a$10$hash")
                .build());

        voo = assetRepository.save(Asset.builder()
                .symbol("VOO")
                .name("Vanguard S&P 500")
                .type(AssetType.ETF)
                .currency("USD")
                .active(true)
                .build());

        // Fichas de sobra para las compras del caso: 628 + 528 = 1156.
        walletService.record(ana, new BigDecimal("2000.00"), TokenReason.ADJUSTMENT, null);
    }

    @AfterEach
    void cleanUp() {
        DatabaseCleaner.clean(jdbc);
    }

    @Test
    @DisplayName("Dos compras a precios distintos dan el coste medio ponderado exacto")
    void weightedAverageCostIsExact() {
        buy("10", "62.80");
        buy("10", "52.80");

        // previousCost = 10 x 62.80 = 628.00
        // addedCost    = 10 x 52.80 = 528.00
        // (628 + 528) / 20 = 1156 / 20 = 57.80
        PositionResponse position = onlyPosition();
        assertThat(position.getQuantity()).isEqualByComparingTo("20");
        assertThat(position.getAverageCost()).isEqualByComparingTo("57.80");
    }

    @Test
    @DisplayName("Vender no altera el coste medio de lo que queda")
    void sellingDoesNotCorruptTheAverageCost() {
        buy("10", "62.80");
        buy("10", "52.80");
        sell("5", "70.00");

        PositionResponse position = onlyPosition();
        assertThat(position.getQuantity()).isEqualByComparingTo("15");
        // Vender no cambia a que precio se compro.
        assertThat(position.getAverageCost()).isEqualByComparingTo("57.80");
    }

    @Test
    @DisplayName("El PnL realizado de una venta con ganancia queda registrado")
    void realizedPnlIsRecorded() {
        buy("10", "62.80");
        buy("10", "52.80");
        sell("5", "70.00");

        // proceeds     = 5 x 70.00 = 350.00
        // releasedCost = 5 x 57.80 = 289.00
        // realizado    = 350.00 - 289.00 = 61.00
        PortfolioResponse portfolio = portfolioService.getSummary(ana);
        assertThat(portfolio.getRealizedPnl()).isEqualByComparingTo("61.00");

        // Y el invertido baja por coste medio, no por importe de venta:
        // 1156.00 - 289.00 = 867.00
        assertThat(portfolio.getInvestedTokens()).isEqualByComparingTo("867.00");
    }

    @Test
    @DisplayName("El PnL realizado de una venta con perdida es negativo")
    void realizedPnlCanBeNegative() {
        buy("10", "50.00");
        sell("10", "40.00");

        // 10 x 40 - 10 x 50 = 400 - 500 = -100
        assertThat(portfolioService.getSummary(ana).getRealizedPnl())
                .isEqualByComparingTo("-100.00");
    }

    @Test
    @DisplayName("El PnL no realizado sigue el precio en ambas direcciones")
    void unrealizedPnlFollowsThePriceBothWays() {
        buy("10", "50.00");

        // Precio 60: 10 x 60 - 10 x 50 = +100
        quote("60.00");
        assertThat(onlyPosition().getUnrealizedPnl()).isEqualByComparingTo("100.00");
        assertThat(portfolioService.getSummary(ana).getUnrealizedPnl())
                .isEqualByComparingTo("100.00");

        // Precio 40: 10 x 40 - 10 x 50 = -100
        quote("40.00");
        assertThat(onlyPosition().getUnrealizedPnl()).isEqualByComparingTo("-100.00");
        assertThat(portfolioService.getSummary(ana).getUnrealizedPnl())
                .isEqualByComparingTo("-100.00");
    }

    @Test
    @DisplayName("Una posicion vendida entera desaparece del listado")
    void aFullySoldPositionDisappears() {
        buy("10", "50.00");
        sell("10", "50.00");

        assertThat(portfolioService.listPositions(ana)).isEmpty();
        assertThat(portfolioService.getSummary(ana).getRealizedPnl())
                .isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("Un activo sin cotizacion deja los derivados en null, no en cero")
    void aPositionWithoutQuoteReportsUnknownAndNotZero() {
        buy("10", "50.00");
        quoteRepository.deleteAll();

        PositionResponse position = onlyPosition();
        assertThat(position.getQuantity()).isEqualByComparingTo("10");
        assertThat(position.getCurrentPrice()).isNull();
        assertThat(position.getMarketValue()).isNull();
        assertThat(position.getUnrealizedPnl()).isNull();
    }

    // ------------------------------------------------------------------
    // Ayudas
    // ------------------------------------------------------------------

    private void buy(String quantity, String price) {
        quote(price);
        TradeOrderResponse order = place(OrderSide.BUY, quantity);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.EXECUTED);
    }

    private void sell(String quantity, String price) {
        quote(price);
        TradeOrderResponse order = place(OrderSide.SELL, quantity);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.EXECUTED);
    }

    private TradeOrderResponse place(OrderSide side, String quantity) {
        TradeOrderRequest request = new TradeOrderRequest();
        request.setClientOrderId(UUID.randomUUID());
        request.setAssetId(voo.getId());
        request.setSide(side);
        request.setQuantity(new BigDecimal(quantity));
        return orderService.place(ana, request);
    }

    /**
     * Fija el precio del activo. Escribe por repositorio y no por AssetService
     * porque updateQuote es @PreAuthorize("hasRole('ADMIN')") y aqui no hace falta
     * montar un SecurityContext: lo que se prueba es la aritmetica, no el permiso.
     */
    private void quote(String price) {
        AssetQuote existing = quoteRepository.findByAssetId(voo.getId()).orElse(null);
        if (existing == null) {
            quoteRepository.save(AssetQuote.builder()
                    .asset(voo)
                    .price(new BigDecimal(price))
                    .updatedAt(LocalDateTime.now())
                    .build());
        } else {
            existing.setPrice(new BigDecimal(price));
            existing.setUpdatedAt(LocalDateTime.now());
            quoteRepository.save(existing);
        }
    }

    private PositionResponse onlyPosition() {
        var positions = portfolioService.listPositions(ana);
        assertThat(positions).hasSize(1);
        return positions.get(0);
    }
}
