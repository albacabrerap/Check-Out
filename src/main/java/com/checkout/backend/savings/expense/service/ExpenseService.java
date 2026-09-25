package com.checkout.backend.savings.expense.service;

import com.checkout.backend.exceptions.InvalidRequestException;
import com.checkout.backend.exceptions.ResourceNotFoundException;
import com.checkout.backend.savings.expense.dto.ExpenseRequest;
import com.checkout.backend.savings.expense.dto.ExpenseResponse;
import com.checkout.backend.savings.expense.model.Expense;
import com.checkout.backend.savings.expense.model.ExpenseCategory;
import com.checkout.backend.savings.expense.repository.ExpenseRepository;
import com.checkout.backend.savings.service.SavingsService;
import com.checkout.backend.user.model.User;
import com.checkout.backend.web.PageResponse;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Gastos del usuario. Es el lado que resta del saldo de ahorro.
 */
@Service
public class ExpenseService {

    private final ExpenseRepository expenseRepository;
    private final SavingsService savingsService;
    private final ModelMapper mapper;

    public ExpenseService(ExpenseRepository expenseRepository,
                          SavingsService savingsService,
                          ModelMapper mapper) {
        this.expenseRepository = expenseRepository;
        this.savingsService = savingsService;
        this.mapper = mapper;
    }

    /**
     * Registra un gasto y lo descuenta del saldo disponible.
     *
     * El descuento va primero: si no hay saldo, debit lanza 400 y el gasto no
     * llega a guardarse. Al reves quedaria registrado un gasto que el saldo no
     * puede sostener.
     */
    @Transactional
    public ExpenseResponse create(User user, ExpenseRequest request) {
        savingsService.debit(user, request.getAmount());

        Expense expense = Expense.builder()
                .user(user)
                .category(request.getCategory())
                .amount(request.getAmount())
                .date(request.getDate())
                .description(request.getDescription())
                .build();

        return mapper.map(expenseRepository.save(expense), ExpenseResponse.class);
    }

    /**
     * Gastos del usuario, opcionalmente por rango de fechas o por categoria.
     *
     * Los dos filtros no se combinan: hacerlo multiplicaria los metodos del
     * repositorio por cada combinacion. Cuando haga falta, el paso siguiente es
     * una Specification, no un metodo mas.
     */
    @Transactional(readOnly = true)
    public PageResponse<ExpenseResponse> list(User user, LocalDate from, LocalDate to,
                                              ExpenseCategory category, Pageable pageable) {
        if (category != null && (from != null || to != null)) {
            throw new InvalidRequestException(
                    "Filtra por categoria o por rango de fechas, no por ambos a la vez.");
        }

        Page<Expense> expenses;
        if (category != null) {
            expenses = expenseRepository.findByUserIdAndCategoryOrderByDateDesc(
                    user.getId(), category, pageable);
        } else if (from == null && to == null) {
            expenses = expenseRepository.findByUserIdOrderByDateDesc(user.getId(), pageable);
        } else if (from == null || to == null) {
            throw new InvalidRequestException(
                    "Para filtrar por fecha hay que enviar 'from' y 'to', no solo uno.");
        } else if (from.isAfter(to)) {
            throw new InvalidRequestException("'from' no puede ser posterior a 'to'.");
        } else {
            expenses = expenseRepository.findByUserIdAndDateBetweenOrderByDateDesc(
                    user.getId(), from, to, pageable);
        }

        return PageResponse.of(expenses.map(e -> mapper.map(e, ExpenseResponse.class)));
    }

    @Transactional(readOnly = true)
    public ExpenseResponse get(User user, Long id) {
        return mapper.map(findOwned(user, id), ExpenseResponse.class);
    }

    /**
     * Corrige un gasto ya registrado.
     *
     * Igual que en los ingresos, el saldo se ajusta por la diferencia. Aqui el
     * signo va al reves: subir el importe de un gasto cobra la diferencia y por
     * tanto valida contra el disponible, y bajarlo la devuelve.
     */
    @Transactional
    public ExpenseResponse update(User user, Long id, ExpenseRequest request) {
        Expense expense = findOwned(user, id);

        BigDecimal difference = request.getAmount().subtract(expense.getAmount());
        if (difference.signum() > 0) {
            savingsService.debit(user, difference);
        } else if (difference.signum() < 0) {
            savingsService.refund(user, difference.abs());
        }

        expense.setCategory(request.getCategory());
        expense.setAmount(request.getAmount());
        expense.setDate(request.getDate());
        expense.setDescription(request.getDescription());

        return mapper.map(expense, ExpenseResponse.class);
    }

    /**
     * Borra el gasto y devuelve el dinero al saldo.
     */
    @Transactional
    public void delete(User user, Long id) {
        Expense expense = findOwned(user, id);
        savingsService.refund(user, expense.getAmount());
        expenseRepository.delete(expense);
    }

    private Expense findOwned(User user, Long id) {
        return expenseRepository.findByIdAndUserId(id, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Gasto", id));
    }

}
