package com.stockmate.product;

import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.stockmate.catalog.CategoryService;
import com.stockmate.common.ApiException;
import com.stockmate.common.ErrorCode;
import com.stockmate.common.Money;
import com.stockmate.common.PageResponse;
import com.stockmate.partner.PartnerService;
import com.stockmate.product.ProductDtos.CreateProductRequest;
import com.stockmate.product.ProductDtos.ProductResponse;
import com.stockmate.product.ProductDtos.UpdateProductRequest;
import com.stockmate.purchase.PurchaseOrderRepository;
import com.stockmate.sales.SalesOrderRepository;
import com.stockmate.stock.MovementType;
import com.stockmate.stock.StockMovementRepository;
import com.stockmate.stock.StockService;

/**
 * Business rules for products.
 * قواعد العمل للمنتجات.
 */
@Service
public class ProductService {

    private final ProductRepository products;
    private final CategoryService categories;
    private final PartnerService partners;
    private final StockService stock;
    private final StockMovementRepository movements;
    private final PurchaseOrderRepository purchaseOrders;
    private final SalesOrderRepository salesOrders;

    public ProductService(ProductRepository products, CategoryService categories, PartnerService partners,
                          StockService stock, StockMovementRepository movements,
                          PurchaseOrderRepository purchaseOrders, SalesOrderRepository salesOrders) {
        this.products = products;
        this.categories = categories;
        this.partners = partners;
        this.stock = stock;
        this.movements = movements;
        this.purchaseOrders = purchaseOrders;
        this.salesOrders = salesOrders;
    }

    /**
     * Paginated, filterable product list.
     * قائمة المنتجات مع التقسيم إلى صفحات والفلاتر.
     */
    @Transactional(readOnly = true)
    public PageResponse<ProductResponse> search(String q, Long categoryId, Long supplierId, StockStatus status,
                                                Boolean active, Pageable pageable) {
        // Collect only the filters that are in use (each helper returns null when its filter is empty),
        // then combine them with AND. Spring Data 4 rejects null parts, so we drop them first.
        // نجمع الفلاتر المستخدمة فقط (كل دالة ترجّع null لو الفلتر فاضي)، ثم نربطها بـ AND.
        // Spring Data 4 يرفض الأجزاء الفارغة، فنشيلها قبل الدمج.
        List<Specification<Product>> filters = Stream.of(
                        ProductSpecifications.matches(q),
                        ProductSpecifications.inCategory(categoryId),
                        ProductSpecifications.fromSupplier(supplierId),
                        ProductSpecifications.withStatus(status),
                        ProductSpecifications.active(active))
                .filter(Objects::nonNull)
                .toList();
        Specification<Product> spec = Specification.allOf(filters);
        Page<Product> page = products.findAll(spec, pageable);
        return PageResponse.of(page, ProductResponse::from);
    }

    @Transactional(readOnly = true)
    public ProductResponse get(Long id) {
        return ProductResponse.from(product(id));
    }

    @Transactional
    public ProductResponse create(CreateProductRequest r) {
        String sku = normalizeSku(r.sku());
        if (products.existsBySkuIgnoreCase(sku)) {
            throw new ApiException(ErrorCode.SKU_EXISTS);
        }
        Product product = new Product(sku, r.name().trim(), categories.get(r.categoryId()));
        product.setSupplier(r.supplierId() == null ? null : partners.supplier(r.supplierId()));
        product.setUnit(r.unit().trim());
        product.setCostPrice(Money.round(r.costPrice()));
        product.setSalePrice(Money.round(r.salePrice()));
        product.setReorderLevel(r.reorderLevel());
        // saveAndFlush gives us the generated id now, which the opening movement needs.
        // saveAndFlush يعطينا رقم المنتج فورًا، والحركة الافتتاحية تحتاجه.
        Product saved = products.saveAndFlush(product);
        if (r.openingQuantity() > 0) {
            // Opening stock goes through the ledger like every other change, so the history starts complete.
            // الرصيد الافتتاحي يمر عبر الدفتر مثل أي تغيير، فيبدأ السجل كامل من أول يوم.
            stock.record(saved.getId(), MovementType.OPENING, r.openingQuantity(), null, null);
        }
        return ProductResponse.from(saved);
    }

    @Transactional
    public ProductResponse update(Long id, UpdateProductRequest r) {
        Product product = product(id);
        // Manual optimistic-lock check: the client sends the version it loaded. If it differs, another user
        // (or a sale) changed the product since, so we refuse instead of silently overwriting their change.
        // فحص القفل المتفائل يدويًا: العميل يرسل النسخة اللي حمّلها، ولو اختلفت يعني مستخدم ثاني (أو عملية بيع)
        // غيّر المنتج، فنرفض بدل ما نمسح تعديله بصمت.
        if (!Objects.equals(product.getVersion(), r.version())) {
            throw new ApiException(ErrorCode.CONCURRENT_UPDATE);
        }
        String sku = normalizeSku(r.sku());
        // Only check uniqueness when the SKU actually changes.
        // نتحقق من التكرار فقط لو تغيّر رمز الصنف فعلًا.
        if (!sku.equalsIgnoreCase(product.getSku()) && products.existsBySkuIgnoreCase(sku)) {
            throw new ApiException(ErrorCode.SKU_EXISTS);
        }
        product.setSku(sku);
        product.setName(r.name().trim());
        product.setCategory(categories.get(r.categoryId()));
        product.setSupplier(r.supplierId() == null ? null : partners.supplier(r.supplierId()));
        product.setUnit(r.unit().trim());
        product.setCostPrice(Money.round(r.costPrice()));
        product.setSalePrice(Money.round(r.salePrice()));
        product.setReorderLevel(r.reorderLevel());
        product.setActive(r.active());
        // Flush now so the response carries the new version number.
        // نحفظ فورًا حتى يحمل الرد رقم النسخة الجديد.
        products.flush();
        return ProductResponse.from(product);
    }

    @Transactional
    public void delete(Long id) {
        Product product = product(id);
        // A product with any history (movements or order lines) must be archived (active=false), not deleted,
        // otherwise past invoices and reports would lose their data.
        // المنتج اللي له أي سجل (حركات أو أسطر طلبات) يُؤرشف (active=false) ولا يُحذف،
        // وإلا الفواتير والتقارير القديمة تفقد بياناتها.
        if (movements.existsByProductId(id) || purchaseOrders.existsLineWithProduct(id)
                || salesOrders.existsLineWithProduct(id)) {
            throw new ApiException(ErrorCode.IN_USE);
        }
        products.delete(product);
    }

    public Product product(Long id) {
        return products.findById(id).orElseThrow(() -> ApiException.notFound("product", id));
    }

    // SKUs are stored uppercase so "bev-001" and "BEV-001" are the same code.
    // نخزّن رمز الصنف بحروف كبيرة حتى يكون "bev-001" و"BEV-001" نفس الرمز.
    private static String normalizeSku(String sku) {
        return sku.trim().toUpperCase(java.util.Locale.ROOT);
    }
}
