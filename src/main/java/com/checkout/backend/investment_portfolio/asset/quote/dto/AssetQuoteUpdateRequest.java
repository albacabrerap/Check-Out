package com.checkout.backend.investment_portfolio.asset.quote.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

// New price of an active.
        // price: current price, price > 0.
public record AssetQuoteUpdateRequest(
        @NotNull @DecimalMin(value = "0.0", inclusive = false, message = "The price must be greater than zero")
        BigDecimal price
) {}