package com.stockmate.sales;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.OptimisticLockException;

import org.springframework.stereotype.Component;

import com.stockmate.common.ApiException;
import com.stockmate.common.ErrorCode;

/**
 * Locks one sales order row for the rest of the transaction.
 * يقفل صف طلب بيع واحد لين نهاية المعاملة.
 *
 * <p>Paying, cancelling and the unpaid-order timeout can happen at the same moment (a customer pays in the last
 * second while the timeout runs). Each of them locks the order row first, so they run one after the other. The
 * lock also checks the order's version: if another transaction changed the order after we read it, we stop with
 * CONCURRENT_UPDATE instead of acting on old data.</p>
 * <p>الدفع والإلغاء وانتهاء مهلة الدفع ممكن يصيرون بنفس اللحظة (عميل يدفع بآخر ثانية والمهلة تشتغل). كل وحدة
 * منهم تقفل صف الطلب أول، فيمشون ورا بعض. والقفل يتحقق كمان من نسخة الطلب: لو معاملة ثانية غيّرت الطلب بعد ما
 * قريناه، نوقف بـ CONCURRENT_UPDATE بدل ما نشتغل على بيانات قديمة.</p>
 */
@Component
public class OrderLock {

    private final EntityManager entityManager;

    public OrderLock(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    // Call it before changing the order. / ناديها قبل تعديل الطلب.
    public void lock(SalesOrder order) {
        try {
            entityManager.lock(order, LockModeType.PESSIMISTIC_WRITE);
        } catch (OptimisticLockException e) {
            throw new ApiException(ErrorCode.CONCURRENT_UPDATE);
        }
    }
}
