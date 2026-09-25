package com.checkout.backend.savings.expense.controller;

import com.checkout.backend.savings.expense.dto.ExpenseRequest;
import com.checkout.backend.savings.expense.dto.ExpenseResponse;
import com.checkout.backend.savings.expense.model.ExpenseCategory;
import com.checkout.backend.savings.expense.service.ExpenseService;
import com.checkout.backend.user.service.CurrentUserProvider;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import com.checkout.backend.web.PageResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
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
 * Gastos del usuario autenticado.
 */
@RestController
@RequestMapping("/expenses")
public class ExpenseController {

    private final ExpenseService expenseService;
    private final CurrentUserProvider currentUser;

    public ExpenseController(ExpenseService expenseService, CurrentUserProvider currentUser) {
        this.expenseService = expenseService;
        this.currentUser = currentUser;
    }

    /**
     * GET /api/v1/expenses?category=FOOD o ?from=...&to=...
     *
     * Una categoria que no existe en el enum da 400 en el binding, con el nombre
     * del parametro y el valor recibido en el mensaje.
     */
    @GetMapping
    public ResponseEntity<PageResponse<ExpenseResponse>> list(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) ExpenseCategory category,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(expenseService.list(
                currentUser.requireCurrentUser(), from, to, category, pageable));
    }

    /** GET /api/v1/expenses/{id} */
    @GetMapping("/{id}")
    public ResponseEntity<ExpenseResponse> get(@PathVariable Long id) {
        return ResponseEntity.ok(expenseService.get(currentUser.requireCurrentUser(), id));
    }

    /** POST /api/v1/expenses */
    @PostMapping
    public ResponseEntity<ExpenseResponse> create(@Valid @RequestBody ExpenseRequest request) {
        ExpenseResponse created = expenseService.create(currentUser.requireCurrentUser(), request);

        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(created.getId())
                .toUri();

        return ResponseEntity.created(location).body(created);
    }

    /**
     * PUT /api/v1/expenses/{id}
     *
     * Reemplaza el gasto completo. El saldo se ajusta por la diferencia, asi que
     * subir el importe puede dar 400 si no hay disponible para cubrirla.
     */
    @PutMapping("/{id}")
    public ResponseEntity<ExpenseResponse> update(@PathVariable Long id,
                                                 @Valid @RequestBody ExpenseRequest request) {
        return ResponseEntity.ok(
                expenseService.update(currentUser.requireCurrentUser(), id, request));
    }

    /** DELETE /api/v1/expenses/{id} */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        expenseService.delete(currentUser.requireCurrentUser(), id);
        return ResponseEntity.noContent().build();
    }

}
