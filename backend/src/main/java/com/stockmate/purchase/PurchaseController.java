package com.stockmate.purchase;

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
import com.stockmate.purchase.PurchaseDtos.PurchaseOrderRequest;
import com.stockmate.purchase.PurchaseDtos.PurchaseOrderResponse;
import com.stockmate.purchase.PurchaseDtos.PurchaseOrderSummary;

/**
 * Purchase order endpoints. State changes are explicit POST actions (/receive, /cancel),
 * which is clearer and safer than letting clients PATCH the status field.
 * روابط طلبات الشراء. تغيير الحالة يتم بأوامر POST واضحة (/receive و /cancel)،
 * وهذا أوضح وأأمن من السماح للعميل يعدّل حقل الحالة مباشرة.
 */
@RestController
@RequestMapping("/api/purchase-orders")
public class PurchaseController {

    private final PurchaseService service;

    public PurchaseController(PurchaseService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<PurchaseOrderSummary> list(@RequestParam(required = false) PurchaseStatus status,
                                                   @RequestParam(required = false) Long supplierId,
                                                   @RequestParam(defaultValue = "") String q,
                                                   @PageableDefault(size = 20, sort = {"createdAt", "id"}, direction = Sort.Direction.DESC) Pageable pageable) {
        return service.search(status, supplierId, q, pageable);
    }

    @GetMapping("/{id}")
    public PurchaseOrderResponse get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PurchaseOrderResponse create(@Valid @RequestBody PurchaseOrderRequest request) {
        return service.create(request);
    }

    @PutMapping("/{id}")
    public PurchaseOrderResponse update(@PathVariable Long id, @Valid @RequestBody PurchaseOrderRequest request) {
        return service.update(id, request);
    }

    @PostMapping("/{id}/receive")
    public PurchaseOrderResponse receive(@PathVariable Long id) {
        return service.receive(id);
    }

    @PostMapping("/{id}/cancel")
    public PurchaseOrderResponse cancel(@PathVariable Long id) {
        return service.cancel(id);
    }
}
