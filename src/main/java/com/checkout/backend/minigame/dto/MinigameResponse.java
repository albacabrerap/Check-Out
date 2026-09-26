package com.checkout.backend.minigame.dto;

import com.checkout.backend.minigame.model.MinigameStatus;
import com.checkout.backend.minigame.model.MinigameType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class MinigameResponse {
    private Long id;
    private String title;
    private MinigameType type;
    private String topic;
    private BigDecimal tokenCost;
    private BigDecimal maxTokenReward;
    private MinigameStatus status;
}