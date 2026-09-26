package com.checkout.backend.investment_portfolio.asset.quote.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// updatedAt is exposed on purpose so the UI can say how old the price is.
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class AssetQuoteResponse {
    private String symbol;
    private BigDecimal price;
    private BigDecimal previousClose;
    private BigDecimal changePercent;
    private String currency;
    private LocalDateTime updatedAt;
}