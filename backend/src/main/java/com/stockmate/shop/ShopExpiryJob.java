package com.stockmate.shop;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Runs the unpaid-order timeout every minute. Off in tests, which call ShopService.expireUnpaid() themselves.
 * يشغّل انتهاء مهلة الدفع كل دقيقة. ومطفي في الاختبارات، لأنها تنادي ShopService.expireUnpaid() بنفسها.
 */
@Component
@ConditionalOnProperty(name = "stockmate.shop.expiry-job", havingValue = "true", matchIfMissing = true)
public class ShopExpiryJob {

    private final ShopService shop;

    public ShopExpiryJob(ShopService shop) {
        this.shop = shop;
    }

    @Scheduled(fixedDelay = 60_000, initialDelay = 30_000)
    public void run() {
        shop.expireUnpaid();
    }
}
