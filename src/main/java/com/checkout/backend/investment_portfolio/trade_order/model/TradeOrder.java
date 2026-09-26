package com.checkout.backend.investment_portfolio.trade_order.model;

import com.checkout.backend.investment_portfolio.asset.model.Asset;
import com.checkout.backend.user.model.User;
import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

// A buy or sell instruction, stored whether or not it executed.
@Entity
@Table(
        name = "orders",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_orders_client_order_id", columnNames = "client_order_id"),
        indexes = @Index(name = "idx_orders_user_created", columnList = "user_id, created_at")
)
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class TradeOrder {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_orders_user"))
    private User user;

    @NotNull @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "asset_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_orders_asset"))
    private Asset asset;

    // A double click fails with 409.
    @NotNull
    @Column(name = "client_order_id", nullable = false, updatable = false)
    private UUID clientOrderId;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private OrderSide side;

    @NotNull @DecimalMin(value = "0.0", inclusive = false)
    @Column(nullable = false, precision = 19, scale = 8)
    private BigDecimal quantity;

    @NotNull @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    @Builder.Default
    private OrderStatus status = OrderStatus.PENDING;

    @Column(name = "execution_price", precision = 19, scale = 4)
    private BigDecimal executionPrice;

    @Column(name = "tokens_moved", precision = 19, scale = 2)
    private BigDecimal tokensMoved;

    @DecimalMin(value = "0.0", inclusive = false)
    @Column(name = "token_rate", precision = 19, scale = 8)
    private BigDecimal tokenRate;

    @Size(max = 255)
    @Column(name = "rejection_reason", length = 255)
    private String rejectionReason;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "executed_at")
    private LocalDateTime executedAt;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TradeOrder other)) return false;
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return TradeOrder.class.hashCode();
    }
}
