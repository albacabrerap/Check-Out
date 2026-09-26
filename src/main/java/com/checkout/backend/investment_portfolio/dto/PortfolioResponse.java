package com.checkout.backend.investment_portfolio.dto;

import com.checkout.backend.investment_portfolio.position.dto.PositionResponse;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class PortfolioResponse {
    private Long id;
    private BigDecimal investedTokens;
    private BigDecimal simulatedValue;
    private BigDecimal unrealizedPnl;
    private BigDecimal realizedPnl;
    private List<PositionResponse> positions;
    private LocalDateTime updatedAt;
}