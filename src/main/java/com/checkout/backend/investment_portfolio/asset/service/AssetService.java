package com.checkout.backend.investment_portfolio.asset.service;

import com.checkout.backend.exceptions.DuplicateResourceException;
import com.checkout.backend.exceptions.InvalidRequestException;
import com.checkout.backend.exceptions.ResourceNotFoundException;
import com.checkout.backend.investment_portfolio.asset.dto.AssetRequest;
import com.checkout.backend.investment_portfolio.asset.dto.AssetResponse;
import com.checkout.backend.investment_portfolio.asset.history.dto.AssetPriceResponse;
import com.checkout.backend.investment_portfolio.asset.history.model.AssetPriceHistory;
import com.checkout.backend.investment_portfolio.asset.history.repository.AssetPriceHistoryRepository;
import com.checkout.backend.investment_portfolio.asset.model.Asset;
import com.checkout.backend.investment_portfolio.asset.quote.dto.AssetQuoteResponse;
import com.checkout.backend.investment_portfolio.asset.quote.model.AssetQuote;
import com.checkout.backend.investment_portfolio.asset.quote.repository.AssetQuoteRepository;
import com.checkout.backend.investment_portfolio.asset.repository.AssetRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.modelmapper.ModelMapper;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Common resources.
// Anyone can read, Admins can write.

@Service
public class AssetService {

    /**
     * Cuanto puede envejecer una cotizacion antes de dejar de servir para operar.
     *
     * Siete dias es holgado a proposito: en este simulador los precios los carga
     * un ADMIN a mano, no un proveedor en tiempo real, asi que un umbral corto
     * dejaria el modulo inutilizable un lunes por la manana. Lo que esto corta es
     * el caso que si importa, que es operar contra un precio de hace meses.
     */
    private static final Duration MAX_QUOTE_AGE = Duration.ofDays(7);

    private final AssetRepository assetRepository;
    private final AssetQuoteRepository quoteRepository;
    private final AssetPriceHistoryRepository historyRepository;
    private final ModelMapper mapper;

    public AssetService(AssetRepository assetRepository,
                        AssetQuoteRepository quoteRepository,
                        AssetPriceHistoryRepository historyRepository,
                        ModelMapper mapper) {
        this.assetRepository = assetRepository;
        this.quoteRepository = quoteRepository;
        this.historyRepository = historyRepository;
        this.mapper = mapper;
    }

    // Operable actives
    @Transactional(readOnly = true)
    public List<AssetResponse> listActive() {
        return assetRepository.findByActiveTrueOrderBySymbolAsc().stream()
                .map(asset -> mapper.map(asset, AssetResponse.class))
                .toList();
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional(readOnly = true)
    public List<AssetResponse> listAll() {
        return assetRepository.findAll().stream()
                .map(asset -> mapper.map(asset, AssetResponse.class))
                .toList();
    }

    @Transactional(readOnly = true)
    public AssetResponse get(Long id) { return mapper.map(findById(id), AssetResponse.class); }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public AssetResponse create(AssetRequest request) {
        if (assetRepository.existsBySymbolIgnoreCase(request.getSymbol())) {
            throw new DuplicateResourceException(
                    "Ya existe un activo con el simbolo '" + request.getSymbol() + "'.");
        }

        Asset asset = Asset.builder()
                .symbol(request.getSymbol().toUpperCase())
                .name(request.getName())
                .type(request.getType())
                .currency(request.getCurrency().toUpperCase())
                .active(request.getActive() == null || request.getActive())
                .build();

        return mapper.map(assetRepository.save(asset), AssetResponse.class);
    }

    // Active is no longer active
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public void deactivate(Long id){ findById(id).setActive(false); }

    // Resolves a operable active: 400 for inactive actives.
    @Transactional(readOnly = true)
    public Asset findTradable(Long id) {
        Asset asset = findById(id);
        if (!Boolean.TRUE.equals(asset.getActive())) {
            throw new InvalidRequestException(
                    "El activo " + asset.getSymbol() + " ya no esta disponible para operar.");
        }
        return asset;
    }

    private Asset findById(Long id) {
        return assetRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Activo", id));
    }


    @Transactional(readOnly = true)
    public AssetQuoteResponse getQuote(Long assetId) {
        return mapper.map(requireQuote(findById(assetId)), AssetQuoteResponse.class);
    }

    @Transactional(readOnly = true)
    public AssetQuote requireQuote(Asset asset) {
        AssetQuote quote = quoteRepository.findByAssetId(asset.getId())
                .orElseThrow(() -> new InvalidRequestException(
                        "El activo " + asset.getSymbol() + " todavia no tiene cotizacion."));

        // Gets rid of past prices...
        if (quote.getUpdatedAt().isBefore(LocalDateTime.now().minus(MAX_QUOTE_AGE))) {
            throw new InvalidRequestException(
                    "La cotizacion de " + asset.getSymbol() + " es del "
                            + quote.getUpdatedAt().toLocalDate()
                            + " y esta desactualizada. No se puede operar con ella.");
        }
        return quote;
    }

    // Current price of various actives (indexed by actives)
    @Transactional(readOnly = true)
    public Map<Long, BigDecimal> currentPrices(Collection<Long> assetIds) {
        if (assetIds.isEmpty()) {
            return Map.of();
        }
        return quoteRepository.findByAssetIdIn(assetIds).stream()
                .collect(Collectors.toMap(
                        quote -> quote.getAsset().getId(), AssetQuote::getPrice));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public AssetQuoteResponse updateQuote(Long assetId, BigDecimal price) {
        if (price == null || price.signum() <= 0) {
            throw new InvalidRequestException("El precio debe ser mayor que cero.");
        }

        Asset asset = findById(assetId);
        AssetQuote quote = quoteRepository.findByAssetId(assetId).orElse(null);

        if (quote == null) {
            quote = AssetQuote.builder()
                    .asset(asset)
                    .price(price)
                    .updatedAt(LocalDateTime.now())
                    .build();
        } else {
            BigDecimal previous = quote.getPrice();
            quote.setPreviousClose(previous);
            quote.setPrice(price);
            quote.setChangePercent(percentChange(previous, price));
            quote.setUpdatedAt(LocalDateTime.now());
        }

        AssetQuote saved = quoteRepository.save(quote);
        recordDailyClose(asset, price);

        return mapper.map(saved, AssetQuoteResponse.class);
    }

    // Saves end of day or overloads it.
    private void recordDailyClose(Asset asset, BigDecimal price) {
        LocalDate today = LocalDate.now();

        historyRepository.findByAssetIdAndDateBetweenOrderByDateAsc(asset.getId(), today, today)
                .stream()
                .findFirst()
                .ifPresentOrElse(
                        existing -> existing.setClosePrice(price),
                        () -> historyRepository.save(AssetPriceHistory.builder()
                                .asset(asset)
                                .date(today)
                                .closePrice(price)
                                .build()));
    }

    private static BigDecimal percentChange(BigDecimal previous, BigDecimal current) {
        if (previous == null || previous.signum() == 0) {
            return null;
        }
        return current.subtract(previous)
                .multiply(BigDecimal.valueOf(100))
                .divide(previous, 4, RoundingMode.HALF_UP);
    }

    // Closes an active.
    @Transactional(readOnly = true)
    public List<AssetPriceResponse> priceHistory(Long assetId, LocalDate from, LocalDate to) {
        findById(assetId);

        List<AssetPriceHistory> history;
        if (from == null && to == null) {
            history = historyRepository.findByAssetIdOrderByDateAsc(assetId);
        } else if (from == null || to == null) {
            throw new InvalidRequestException(
                    "Para acotar el historico hay que enviar 'from' y 'to', no solo uno.");
        } else if (from.isAfter(to)) {
            throw new InvalidRequestException("'from' no puede ser posterior a 'to'.");
        } else {
            history = historyRepository.findByAssetIdAndDateBetweenOrderByDateAsc(assetId, from, to);
        }

        return history.stream()
                .map(entry -> mapper.map(entry, AssetPriceResponse.class))
                .toList();
    }
}