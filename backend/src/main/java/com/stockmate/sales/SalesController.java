package com.stockmate.sales;

import jakarta.validation.Valid;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.stockmate.common.PageResponse;
import com.stockmate.sales.SalesDtos.SalesOrderRequest;
import com.stockmate.sales.SalesDtos.SalesOrderResponse;
import com.stockmate.sales.SalesDtos.SalesOrderSummary;

/**
 * Sales order endpoints.
 * روابط طلبات البيع.
 */
@RestController
@RequestMapping("/api/sales-orders")
public class SalesController {

    private final SalesService service;

    public SalesController(SalesService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<SalesOrderSummary> list(@RequestParam(required = false) SalesStatus status,
                                                @RequestParam(required = false) Long customerId,
                                                @RequestParam(defaultValue = "") String q,
                                                @PageableDefault(size = 20, sort = {"createdAt", "id"}, direction = Sort.Direction.DESC) Pageable pageable) {
        return service.search(status, customerId, q, pageable);
    }

    @GetMapping("/{id}")
    public SalesOrderResponse get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SalesOrderResponse create(@Valid @RequestBody SalesOrderRequest request) {
        return service.create(request);
    }

    @PutMapping("/{id}")
    public SalesOrderResponse update(@PathVariable Long id, @Valid @RequestBody SalesOrderRequest request) {
        return service.update(id, request);
    }

    @PostMapping("/{id}/confirm")
    public SalesOrderResponse confirm(@PathVariable Long id) {
        return service.confirm(id);
    }

    @PostMapping("/{id}/cancel")
    public SalesOrderResponse cancel(@PathVariable Long id) {
        return service.cancel(id);
    }
}
