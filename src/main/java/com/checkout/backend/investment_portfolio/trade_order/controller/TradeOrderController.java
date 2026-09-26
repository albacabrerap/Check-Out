package com.checkout.backend.investment_portfolio.trade_order.controller;

import com.checkout.backend.investment_portfolio.trade_order.dto.TradeOrderRequest;
import com.checkout.backend.investment_portfolio.trade_order.dto.TradeOrderResponse;
import com.checkout.backend.investment_portfolio.trade_order.service.TradeOrderService;
import com.checkout.backend.user.service.CurrentUserProvider;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import com.checkout.backend.web.PageResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

// Buy and sell orders of the authenticated user.
@RestController
@RequestMapping("/orders")
public class TradeOrderController {

    private final TradeOrderService orderService;
    private final CurrentUserProvider currentUser;

    public TradeOrderController(TradeOrderService orderService,
                                CurrentUserProvider currentUser) {
        this.orderService = orderService;
        this.currentUser = currentUser;
    }

    // GET /api/v1/orders
    @GetMapping
    public ResponseEntity<PageResponse<TradeOrderResponse>> list(
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(orderService.list(currentUser.requireCurrentUser(), pageable));
    }

    // GET /api/v1/orders/{id}
    @GetMapping("/{id}")
    public ResponseEntity<TradeOrderResponse> get(@PathVariable Long id) {
        return ResponseEntity.ok(orderService.get(currentUser.requireCurrentUser(), id));
    }

    // POST /api/v1/orders
    @PostMapping
    public ResponseEntity<TradeOrderResponse> place(@Valid @RequestBody TradeOrderRequest request) {
        TradeOrderResponse placed = orderService.place(currentUser.requireCurrentUser(), request);

        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(placed.getId())
                .toUri();

        return ResponseEntity.created(location).body(placed);
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<TradeOrderResponse> cancel(@PathVariable Long id) {
        return ResponseEntity.ok(orderService.cancel(currentUser.requireCurrentUser(), id));
    }
}