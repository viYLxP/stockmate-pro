package com.stockmate.partner;

import static com.stockmate.partner.PartnerDtos.clean;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.stockmate.common.ApiException;
import com.stockmate.common.ErrorCode;
import com.stockmate.common.PageResponse;
import com.stockmate.partner.PartnerDtos.CustomerRequest;
import com.stockmate.partner.PartnerDtos.CustomerResponse;
import com.stockmate.partner.PartnerDtos.SupplierRequest;
import com.stockmate.partner.PartnerDtos.SupplierResponse;
import com.stockmate.product.ProductRepository;
import com.stockmate.purchase.PurchaseOrderRepository;
import com.stockmate.sales.SalesOrderRepository;

/**
 * Business rules for suppliers and customers.
 * قواعد العمل للموردين والعملاء.
 */
@Service
public class PartnerService {

    private final SupplierRepository suppliers;
    private final CustomerRepository customers;
    private final ProductRepository products;
    private final PurchaseOrderRepository purchaseOrders;
    private final SalesOrderRepository salesOrders;

    public PartnerService(SupplierRepository suppliers, CustomerRepository customers, ProductRepository products,
                          PurchaseOrderRepository purchaseOrders, SalesOrderRepository salesOrders) {
        this.suppliers = suppliers;
        this.customers = customers;
        this.products = products;
        this.purchaseOrders = purchaseOrders;
        this.salesOrders = salesOrders;
    }

    // ----- Suppliers / الموردين -----

    @Transactional(readOnly = true)
    public PageResponse<SupplierResponse> searchSuppliers(String q, Pageable pageable) {
        return PageResponse.of(suppliers.search(q == null ? "" : q.trim(), pageable), SupplierResponse::from);
    }

    @Transactional(readOnly = true)
    public SupplierResponse getSupplier(Long id) {
        return SupplierResponse.from(supplier(id));
    }

    @Transactional
    public SupplierResponse createSupplier(SupplierRequest request) {
        Supplier supplier = new Supplier();
        applySupplier(supplier, request);
        return SupplierResponse.from(suppliers.save(supplier));
    }

    @Transactional
    public SupplierResponse updateSupplier(Long id, SupplierRequest request) {
        Supplier supplier = supplier(id);
        applySupplier(supplier, request);
        return SupplierResponse.from(supplier);
    }

    @Transactional
    public void deleteSupplier(Long id) {
        Supplier supplier = supplier(id);
        // Keep history intact: a supplier linked to products or purchase orders cannot be deleted.
        // نحافظ على السجل: المورد المرتبط بمنتجات أو طلبات شراء ما ينحذف.
        if (products.existsBySupplierId(id) || purchaseOrders.existsBySupplierId(id)) {
            throw new ApiException(ErrorCode.IN_USE);
        }
        suppliers.delete(supplier);
    }

    public Supplier supplier(Long id) {
        return suppliers.findById(id).orElseThrow(() -> ApiException.notFound("supplier", id));
    }

    private static void applySupplier(Supplier supplier, SupplierRequest r) {
        supplier.applyContact(r.name().trim(), clean(r.phone()), clean(r.email()), clean(r.city()));
        supplier.setVatNumber(clean(r.vatNumber()));
    }

    // ----- Customers / العملاء -----

    @Transactional(readOnly = true)
    public PageResponse<CustomerResponse> searchCustomers(String q, Pageable pageable) {
        return PageResponse.of(customers.search(q == null ? "" : q.trim(), pageable), CustomerResponse::from);
    }

    @Transactional(readOnly = true)
    public CustomerResponse getCustomer(Long id) {
        return CustomerResponse.from(customer(id));
    }

    @Transactional
    public CustomerResponse createCustomer(CustomerRequest r) {
        Customer customer = new Customer();
        customer.applyContact(r.name().trim(), clean(r.phone()), clean(r.email()), clean(r.city()));
        return CustomerResponse.from(customers.save(customer));
    }

    @Transactional
    public CustomerResponse updateCustomer(Long id, CustomerRequest r) {
        Customer customer = customer(id);
        customer.applyContact(r.name().trim(), clean(r.phone()), clean(r.email()), clean(r.city()));
        return CustomerResponse.from(customer);
    }

    @Transactional
    public void deleteCustomer(Long id) {
        Customer customer = customer(id);
        // A customer with sales orders is part of the sales history and cannot be deleted.
        // العميل اللي له طلبات بيع جزء من سجل المبيعات وما ينحذف.
        if (salesOrders.existsByCustomerId(id)) {
            throw new ApiException(ErrorCode.IN_USE);
        }
        customers.delete(customer);
    }

    public Customer customer(Long id) {
        return customers.findById(id).orElseThrow(() -> ApiException.notFound("customer", id));
    }
}
