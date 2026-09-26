package com.checkout.backend.investment_portfolio.asset.history.model;

import com.checkout.backend.investment_portfolio.asset.model.Asset;
import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

// Daily closing price of an asset.
@Entity
@Table(
        name = "asset_price_history",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_asset_price_history_asset_date",
                columnNames = {"asset_id", "date"}),
        indexes = @Index(name = "idx_asset_price_history_asset_date",
                columnList = "asset_id, date")
)
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class AssetPriceHistory {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Pruning the history must never touch the asset.
    @NotNull @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "asset_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_asset_price_history_asset"))
    private Asset asset;

    @NotNull @PastOrPresent(message = "A closing price cannot be dated in the future")
    @Column(nullable = false)
    private LocalDate date;

    @NotNull @DecimalMin(value = "0.0", inclusive = false)
    @Column(name = "close_price", nullable = false, precision = 19, scale = 4)
    private BigDecimal closePrice;

    // Two unsaved instances are only equal to themselves.
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AssetPriceHistory other)) return false;
        return id != null && id.equals(other.id);
    }

    // Hash must not change when the id is assigned on persist.
    @Override
    public int hashCode() { return AssetPriceHistory.class.hashCode(); }
}