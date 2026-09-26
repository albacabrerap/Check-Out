package com.checkout.backend.minigame.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "minigames")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Minigame {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Size(max = 120)
    @Column(nullable = false, length = 120)
    private String title;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MinigameType type;

    @Size(max = 120)
    @Column(length = 120)
    private String topic;

    /**
     * Tokens charged to play. Zero means free. Decimal like every other token
     * amount in the model, so the concept has a single type end to end.
     */
    @NotNull
    @DecimalMin("0")
    @Column(name = "token_cost", nullable = false, precision = 19, scale = 2)
    @Builder.Default
    private BigDecimal tokenCost = BigDecimal.ZERO;

    /** Upper bound of the reward; the actual amount depends on the score. */
    @NotNull @DecimalMin("0") @Column(name = "max_token_reward", nullable = false, precision = 19, scale = 2)
    @Builder.Default
    private BigDecimal maxTokenReward = BigDecimal.ZERO;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private MinigameStatus status = MinigameStatus.DRAFT;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Minigame other)) return false;
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return Minigame.class.hashCode();
    }
}
