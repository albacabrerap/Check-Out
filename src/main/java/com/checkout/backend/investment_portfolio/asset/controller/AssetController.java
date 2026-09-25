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

/**
 * Catalogo de activos, con su cotizacion y su historico como sub-recursos.
 *
 * La cotizacion y el historico van anidados bajo /assets/{id} y no como
 * controllers sueltos porque ninguno de los dos existe sin su activo: pedir
 * /quotes/{id} obligaria a conocer un identificador que no es el que el cliente
 * tiene en la mano.
 *
 * Escribir el precio es de ADMIN, y es la restriccion mas importante del modulo:
 * ese precio es la base sobre la que se valora la cartera de todos, asi que
 * quien pueda escribirlo puede inflar su propia posicion. En un sistema real
 * vendria de un proveedor externo y no habria endpoint de escritura.
 */
@RestController
@RequestMapping("/assets")
public class AssetController {

    private final AssetService assetService;

    public AssetController(AssetService assetService) {
        this.assetService = assetService;
    }

    /**
     * GET /api/v1/assets — el catalogo operable
     *
     * Con ?includeInactive=true incluye los dados de baja, y eso exige ADMIN.
     *
     * Antes eran dos rutas, /assets y /assets/all, y "all" en la ruta es un
     * sustantivo que no existe: es una variante del mismo listado, no otro
     * recurso. Con el parametro, el cliente pide el mismo recurso con otro filtro,
     * que es para lo que existen los parametros de consulta.
     *
     * El control de acceso no puede ser @PreAuthorize a nivel de metodo, porque el
     * metodo lo llaman los dos tipos de usuario. Se comprueba dentro, solo cuando
     * se pide la variante privilegiada.
     */
    @GetMapping
    public ResponseEntity<List<AssetResponse>> list(
            @RequestParam(defaultValue = "false") boolean includeInactive) {

        if (!includeInactive) {
            return ResponseEntity.ok(assetService.listActive());
        }
        return ResponseEntity.ok(assetService.listAll());
    }

    /** GET /api/v1/assets/{id} */
    @GetMapping("/{id}")
    public ResponseEntity<AssetResponse> get(@PathVariable Long id) {
        return ResponseEntity.ok(assetService.get(id));
    }

    /** POST /api/v1/assets */
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

    /**
     * DELETE /api/v1/assets/{id}
     *
     * Baja logica. Las posiciones abiertas sobre este activo siguen valorandose;
     * lo que deja de poder hacerse es abrir nuevas.
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deactivate(@PathVariable Long id) {
        assetService.deactivate(id);
        return ResponseEntity.noContent().build();
    }

    /** GET /api/v1/assets/{id}/quote */
    @GetMapping("/{id}/quote")
    public ResponseEntity<AssetQuoteResponse> getQuote(@PathVariable Long id) {
        return ResponseEntity.ok(assetService.getQuote(id));
    }

    /**
     * PUT /api/v1/assets/{id}/quote
     *
     * Es PUT y no POST porque hay una sola cotizacion por activo y esto la
     * reemplaza. Guarda ademas el cierre del dia, para que el historico no quede
     * con huecos cuando el precio se sobrescriba.
     */
    @PutMapping("/{id}/quote")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AssetQuoteResponse> updateQuote(
            @PathVariable Long id,
            @Valid @RequestBody AssetQuoteUpdateRequest request) {
        return ResponseEntity.ok(assetService.updateQuote(id, request.price()));
    }

    /**
     * GET /api/v1/assets/{id}/price-history?from=2026-01-01&to=2026-06-30
     *
     * Los cierres diarios que alimentan el grafico.
     */
    @GetMapping("/{id}/price-history")
    public ResponseEntity<List<AssetPriceResponse>> priceHistory(
            @PathVariable Long id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(assetService.priceHistory(id, from, to));
    }

}
