package com.checkout.backend.investment_portfolio.asset.history.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

// Price chart. Range of five years...
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class AssetPriceResponse {
    private LocalDate date;
    private BigDecimal closePrice;
}