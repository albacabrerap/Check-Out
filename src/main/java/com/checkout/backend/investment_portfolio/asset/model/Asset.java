package com.checkout.backend.investment_portfolio.asset.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.*;

// Curated catalogue of tradable assets.
// Only active assets are sent to the price provider in the scheduled batch refresh.

@Entity
@Table(
        name = "assets",
        uniqueConstraints = @UniqueConstraint(name = "uk_assets_symbol", columnNames = "symbol")
)
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class Asset {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank @Size(max = 15) @Column(nullable = false, length = 15)
    private String symbol;

    @NotBlank @Size(max = 120) @Column(nullable = false, length = 120)
    private String name;

    @NotNull @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private AssetType type;

    // Display decision stays in service layer: the provider quotes US equities in USD.
    @NotBlank @Pattern(regexp = "^[A-Z]{3}$", message = "Currency must be a 3-letter ISO code")
    @Column(nullable = false, length = 3)
    @Builder.Default
    private String currency = "USD";

    @NotNull @Column(nullable = false)
    @Builder.Default
    private Boolean active = Boolean.TRUE;

    // Two unsaved instances are only equal to themselves.
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Asset other)) return false;
        return id != null && id.equals(other.id);
    }

    // Hash must not change when the id is assigned on persist.
    @Override
    public int hashCode() {
        return Asset.class.hashCode();
    }
}