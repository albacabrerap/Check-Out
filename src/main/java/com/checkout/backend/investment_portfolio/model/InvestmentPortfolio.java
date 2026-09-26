package com.checkout.backend.investment_portfolio.model;

import com.checkout.backend.investment_portfolio.position.model.PortfolioPosition;
import com.checkout.backend.user.model.User;
import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

// Simulated investment account. One per user. Uses game tokens.
@Entity
@Table(
        name = "investment_portfolios",
        uniqueConstraints = @UniqueConstraint(name = "uk_portfolios_user", columnNames = "user_id")
)
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class InvestmentPortfolio {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true,
            foreignKey = @ForeignKey(name = "fk_portfolios_user"))
    private User user;

    @NotNull @DecimalMin("0") @Column(name = "invested_tokens", nullable = false, precision = 19, scale = 2)
    @Builder.Default
    private BigDecimal investedTokens = BigDecimal.ZERO;

    // Cache derived from the positions and the latest quotes.
    @NotNull @DecimalMin("0")
    @Column(name = "simulated_value", nullable = false, precision = 19, scale = 2)
    @Builder.Default
    private BigDecimal simulatedValue = BigDecimal.ZERO;

    // Acumulated sells result.
    @NotNull @Column(name = "realized_pnl", nullable = false, precision = 19, scale = 2)
    @Builder.Default
    private BigDecimal realizedPnl = BigDecimal.ZERO;

    @OneToMany(mappedBy = "portfolio", cascade = CascadeType.ALL,
            orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<PortfolioPosition> positions = new ArrayList<>();

    // Both amounts are caches over the positions.
    @Version @Column(nullable = false)
    private Long version;

    @UpdateTimestamp @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public void addPosition(PortfolioPosition position) {
        positions.add(position);
        position.setPortfolio(this);
    }

    public void removePosition(PortfolioPosition position) {
        positions.remove(position);
        position.setPortfolio(null);
    }

    // Two unsaved instances are only equal to themselves.
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof InvestmentPortfolio other)) return false;
        return id != null && id.equals(other.id);
    }

    // Hash must not change when the id is assigned on persist.
    @Override
    public int hashCode() { return InvestmentPortfolio.class.hashCode(); }
}