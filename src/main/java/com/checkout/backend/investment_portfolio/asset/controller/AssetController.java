package com.checkout.backend.investment_portfolio.asset.controller;

import com.checkout.backend.investment_portfolio.asset.dto.AssetRequest;
import com.checkout.backend.investment_portfolio.asset.dto.AssetResponse;
import com.checkout.backend.investment_portfolio.asset.history.dto.AssetPriceResponse;
import com.checkout.backend.investment_portfolio.asset.quote.dto.AssetQuoteResponse;
import com.checkout.backend.investment_portfolio.asset.quote.dto.AssetQuoteUpdateRequest;
import com.checkout.backend.investment_portfolio.asset.service.AssetService;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

// Catalogue of actives, price, history and sub-resources.
// Prices, history -> /assets/{id}

@RestController
@RequestMapping("/assets")
public class AssetController {
    private final AssetService assetService;
    public AssetController(AssetService assetService) {
        this.assetService = assetService;
    }

    // GET /api/v1/assets
    @GetMapping
    public ResponseEntity<List<AssetResponse>> list(
            @RequestParam(defaultValue = "false") boolean includeInactive) {

        if (!includeInactive) {
            return ResponseEntity.ok(assetService.listActive());
        }
        return ResponseEntity.ok(assetService.listAll());
    }

    // GET /api/v1/assets/{id}
    @GetMapping("/{id}")
    public ResponseEntity<AssetResponse> get(@PathVariable Long id) {
        return ResponseEntity.ok(assetService.get(id));
    }

    // POST /api/v1/assets
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AssetResponse> create(@Valid @RequestBody AssetRequest request) {
        AssetResponse created = assetService.create(request);

        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(created.getId())
                .toUri();

        return ResponseEntity.created(location).body(created);
    }

    // DELETE /api/v1/assets/{id}
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deactivate(@PathVariable Long id) {
        assetService.deactivate(id);
        return ResponseEntity.noContent().build();
    }

    // GET /api/v1/assets/{id}/quote
    @GetMapping("/{id}/quote")
    public ResponseEntity<AssetQuoteResponse> getQuote(@PathVariable Long id) {
        return ResponseEntity.ok(assetService.getQuote(id));
    }

    // PUT /api/v1/assets/{id}/quote: one price per active
    @PutMapping("/{id}/quote")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AssetQuoteResponse> updateQuote(
            @PathVariable Long id,
            @Valid @RequestBody AssetQuoteUpdateRequest request) {
        return ResponseEntity.ok(assetService.updateQuote(id, request.price()));
    }

    // GET /api/v1/assets/{id}/price-history?from=2026-01-01&to=2026-06-30
    @GetMapping("/{id}/price-history")
    public ResponseEntity<List<AssetPriceResponse>> priceHistory(
            @PathVariable Long id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(assetService.priceHistory(id, from, to));
    }
}