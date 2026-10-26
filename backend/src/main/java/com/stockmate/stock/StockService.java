package com.stockmate.stock;

import java.time.Instant;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.stockmate.common.ApiException;
import com.stockmate.common.CurrentUser;
import com.stockmate.common.PageResponse;
import com.stockmate.product.Product;
import com.stockmate.product.ProductRepository;
import com.stockmate.stock.StockDtos.AdjustmentRequest;
import com.stockmate.stock.StockDtos.Direction;
import com.stockmate.stock.StockDtos.MovementResponse;

/**
 * The ONLY place that changes product quantities.
 * المكان الوحيد اللي يغيّر كميات المنتجات.
 *
 * <p>Purchases, sales and adjustments all go through {@link #record}, so the quantity update and its
 * audit record always happen together, in the same transaction.</p>
 * <p>المشتريات والمبيعات والتسويات كلها تمر عبر {@link #record}، فتحديث الكمية وسجله يحدثان
 * دائمًا معًا وفي نفس المعاملة.</p>
 */
@Service
public class StockService {

    private final ProductRepository products;
    private final StockMovementRepository movements;
    private final EntityManager entityManager;

    public StockService(ProductRepository products, StockMovementRepository movements, EntityManager entityManager) {
        this.products = products;
        this.movements = movements;
        this.entityManager = entityManager;
    }

    /**
     * Applies a stock change and writes its ledger entry.
     * يطبّق تغيير المخزون ويكتب قيده في الدفتر.
     *
     * <p>{@code Propagation.MANDATORY}: must be called inside an existing transaction (e.g. confirming an order),
     * so a failure on any line rolls back every line of the order.</p>
     * <p>{@code Propagation.MANDATORY}: لازم يُستدعى داخل معاملة موجودة (مثل تأكيد طلب)،
     * فلو فشل أي سطر يتراجع الطلب كامل.</p>
     *
     * @param productId product to change / المنتج المطلوب
     * @param type      reason; its direction decides the sign / السبب، واتجاهه يحدد الإشارة
     * @param units     positive number of units / عدد الوحدات بقيمة موجبة
     * @param reference source document, nullable / المستند المصدر ويمكن يكون فاضي
     * @param note      free text, nullable / ملاحظة اختيارية
     * @return the saved movement / الحركة المحفوظة
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public StockMovement record(Long productId, MovementType type, int units, String reference, String note) {
        if (units <= 0) {
            // A programming error, not a user error: callers must always pass a positive count.
            // هذا خطأ برمجي وليس خطأ مستخدم: المستدعي لازم يمرر عددًا موجبًا دائمًا.
            throw new IllegalArgumentException("units must be positive");
        }
        Product product = products.findById(productId)
                .orElseThrow(() -> ApiException.notFound("product", productId));
        // Row lock (SELECT ... FOR UPDATE): concurrent changes to the same product wait for each other,
        // so no update is lost and stock is never oversold.
        // We use refresh() rather than a locking query on purpose: the product may already be loaded in this
        // transaction (e.g. through the order lines), and Hibernate would keep that possibly stale copy.
        // refresh() re-reads the row while taking the lock, so we always work with the latest quantity.
        // قفل الصف (SELECT ... FOR UPDATE): التغييرات المتزامنة على نفس المنتج تنتظر بعضها،
        // فما يضيع أي تحديث وما نبيع أكثر من المتوفر.
        // نستخدم refresh() بدل استعلام قفل عن قصد: المنتج ممكن يكون محمّل مسبقًا في نفس المعاملة
        // (مثلًا من أسطر الطلب)، وHibernate بيحتفظ بالنسخة القديمة. refresh() يعيد قراءة الصف مع القفل،
        // فنشتغل دائمًا على أحدث كمية.
        entityManager.refresh(product, LockModeType.PESSIMISTIC_WRITE);
        int delta = type.isInbound() ? units : -units;
        // Throws INSUFFICIENT_STOCK if the balance would become negative.
        // يرمي INSUFFICIENT_STOCK لو صار الرصيد سالبًا.
        int balance = product.changeQuantity(delta);
        return movements.save(new StockMovement(product, type, delta, balance, reference, note, CurrentUser.username()));
    }

    /**
     * Manual adjustment endpoint logic.
     * منطق التسوية اليدوية.
     */
    @Transactional
    public MovementResponse adjust(AdjustmentRequest request) {
        MovementType type = request.direction() == Direction.IN ? MovementType.ADJUSTMENT_IN : MovementType.ADJUSTMENT_OUT;
        return MovementResponse.from(record(request.productId(), type, request.quantity(), null, request.note().trim()));
    }

    @Transactional(readOnly = true)
    public PageResponse<MovementResponse> search(Long productId, MovementType type, Instant from, Instant to,
                                                 Pageable pageable) {
        return PageResponse.of(movements.search(productId, type, from, to, pageable), MovementResponse::from);
    }
}
