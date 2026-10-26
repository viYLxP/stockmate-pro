package com.stockmate.product;

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
import com.stockmate.product.ProductDtos.CreateProductRequest;
import com.stockmate.product.ProductDtos.ProductResponse;
import com.stockmate.product.ProductDtos.UpdateProductRequest;

/**
 * Product endpoints.
 * روابط المنتجات.
 *
 * <p>Example: {@code GET /api/products?q=rice&status=LOW&page=0&size=20&sort=name,asc}</p>
 * <p>مثال: {@code GET /api/products?q=rice&status=LOW&page=0&size=20&sort=name,asc}</p>
 */
@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductService service;

    public ProductController(ProductService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<ProductResponse> list(@RequestParam(required = false) String q,
                                              @RequestParam(required = false) Long categoryId,
                                              @RequestParam(required = false) Long supplierId,
                                              @RequestParam(required = false) StockStatus status,
                                              @RequestParam(required = false) Boolean active,
                                              @PageableDefault(size = 20, sort = {"name", "id"}, direction = Sort.Direction.ASC) Pageable pageable) {
        return service.search(q, categoryId, supplierId, status, active, pageable);
    }

    @GetMapping("/{id}")
    public ProductResponse get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProductResponse create(@Valid @RequestBody CreateProductRequest request) {
        return service.create(request);
    }

    @PutMapping("/{id}")
    public ProductResponse update(@PathVariable Long id, @Valid @RequestBody UpdateProductRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
