package com.stockmate.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;

import com.stockmate.support.IntegrationTest;

/**
 * Proves that the row lock prevents overselling when many users confirm orders at the same time.
 * يثبت إن قفل الصف يمنع البيع أكثر من المتوفر لما يأكد مستخدمين كثير طلباتهم بنفس اللحظة.
 */
class ConcurrentSalesTest extends IntegrationTest {

    @Test
    void tenParallelConfirmationsForFiveUnitsSellExactlyFive() throws Exception {
        String token = staffToken();
        long productId = product(token, "HOT-1", category(token, "Hot"), "1", "2", 0, 5);
        long customerId = customer(token, "Rush Buyer");
        List<Long> orderIds = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            orderIds.add(createId("/api/sales-orders", """
                    {"customerId":%d,"lines":[{"productId":%d,"quantity":1}]}
                    """.formatted(customerId, productId), token));
        }

        ExecutorService pool = Executors.newFixedThreadPool(10);
        // The latch releases all threads at once to maximise the chance of a race.
        // الـ latch يطلق كل الخيوط مرة وحدة حتى نزيد احتمال التسابق.
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> results = new ArrayList<>();
        for (long orderId : orderIds) {
            Callable<Integer> confirm = () -> {
                start.await();
                return mvc.perform(auth(post("/api/sales-orders/" + orderId + "/confirm"), token))
                        .andReturn().getResponse().getStatus();
            };
            results.add(pool.submit(confirm));
        }
        start.countDown();
        int ok = 0;
        int conflict = 0;
        for (Future<Integer> result : results) {
            int status = result.get();
            if (status == 200) {
                ok++;
            } else if (status == 409) {
                conflict++;
            }
        }
        pool.shutdown();

        // Exactly 5 sales succeed, the other 5 get INSUFFICIENT_STOCK, and stock ends at 0 (never negative).
        // بالضبط 5 مبيعات تنجح، والـ 5 الباقية ترجع INSUFFICIENT_STOCK، والمخزون ينتهي عند صفر (مو سالب).
        assertThat(ok).isEqualTo(5);
        assertThat(conflict).isEqualTo(5);
        assertThat(quantityOf(productId)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from stock_movement where type = 'SALE'", Integer.class))
                .isEqualTo(5);
    }
}
