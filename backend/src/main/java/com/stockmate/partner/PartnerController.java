package com.stockmate.partner;

import jakarta.validation.Valid;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
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
import com.stockmate.partner.PartnerDtos.CustomerRequest;
import com.stockmate.partner.PartnerDtos.CustomerResponse;
import com.stockmate.partner.PartnerDtos.SupplierRequest;
import com.stockmate.partner.PartnerDtos.SupplierResponse;

/**
 * Supplier and customer endpoints.
 * روابط الموردين والعملاء.
 */
@RestController
@RequestMapping("/api")
public class PartnerController {

    private final PartnerService service;

    public PartnerController(PartnerService service) {
        this.service = service;
    }

    // Default: 20 per page, sorted by name. The client can override with ?page=&size=&sort=.
    // الافتراضي 20 بالصفحة مرتبة بالاسم، والعميل يقدر يغيّرها بـ ?page=&size=&sort=.
    @GetMapping("/suppliers")
    public PageResponse<SupplierResponse> suppliers(@RequestParam(defaultValue = "") String q,
                                                    @PageableDefault(size = 20, sort = "name", direction = Sort.Direction.ASC) Pageable pageable) {
        return service.searchSuppliers(q, pageable);
    }

    @GetMapping("/suppliers/{id}")
    public SupplierResponse supplier(@PathVariable Long id) {
        return service.getSupplier(id);
    }

    @PostMapping("/suppliers")
    @ResponseStatus(HttpStatus.CREATED)
    public SupplierResponse createSupplier(@Valid @RequestBody SupplierRequest request) {
        return service.createSupplier(request);
    }

    @PutMapping("/suppliers/{id}")
    public SupplierResponse updateSupplier(@PathVariable Long id, @Valid @RequestBody SupplierRequest request) {
        return service.updateSupplier(id, request);
    }

    @DeleteMapping("/suppliers/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteSupplier(@PathVariable Long id) {
        service.deleteSupplier(id);
    }

    @GetMapping("/customers")
    public PageResponse<CustomerResponse> customers(@RequestParam(defaultValue = "") String q,
                                                    @PageableDefault(size = 20, sort = "name", direction = Sort.Direction.ASC) Pageable pageable) {
        return service.searchCustomers(q, pageable);
    }

    @GetMapping("/customers/{id}")
    public CustomerResponse customer(@PathVariable Long id) {
        return service.getCustomer(id);
    }

    @PostMapping("/customers")
    @ResponseStatus(HttpStatus.CREATED)
    public CustomerResponse createCustomer(@Valid @RequestBody CustomerRequest request) {
        return service.createCustomer(request);
    }

    @PutMapping("/customers/{id}")
    public CustomerResponse updateCustomer(@PathVariable Long id, @Valid @RequestBody CustomerRequest request) {
        return service.updateCustomer(id, request);
    }

    @DeleteMapping("/customers/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteCustomer(@PathVariable Long id) {
        service.deleteCustomer(id);
    }
}
