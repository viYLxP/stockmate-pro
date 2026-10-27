package com.stockmate.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

import com.stockmate.support.IntegrationTest;

/**
 * The public shop refuses a visitor who sends too many orders or lookups.
 * المتجر العام يرفض الزائر اللي يرسل طلبات أو عمليات بحث كثيرة.
 */
@TestPropertySource(properties = {"stockmate.rate-limit.orders=2", "stockmate.rate-limit.lookups=3"})
class RateLimitTest extends IntegrationTest {

    @Test
    void ordersAndLookupsHaveSeparateLimitsPerIp() throws Exception {
        String admin = adminToken();
        long rice = shopProduct(admin, "RICE", category(admin, "Food"), "10.00", 50);
        String body = checkoutJson("[{\"productId\":%d,\"quantity\":1}]".formatted(rice), "0551234567", "CASH");
        mvc.perform(json(post("/api/shop/checkout")).content(body)).andExpect(status().isCreated());
        mvc.perform(json(post("/api/shop/checkout")).content(body)).andExpect(status().isCreated());
        mvc.perform(json(post("/api/shop/checkout")).content(body))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"));
        // Another IP is not affected. / عنوان ثاني ما يتأثر.
        mvc.perform(json(post("/api/shop/checkout")).content(body).with(r -> {
            r.setRemoteAddr("10.9.9.9");
            return r;
        })).andExpect(status().isCreated());

        // Guessing phone numbers on the tracking endpoint is limited too. / تخمين الأرقام في التتبع محدود كمان.
        String track = "{\"number\":\"SO-999999\",\"phone\":\"0550000000\"}";
        for (int i = 0; i < 3; i++) {
            mvc.perform(json(post("/api/shop/orders/track")).content(track)).andExpect(status().isNotFound());
        }
        mvc.perform(json(post("/api/shop/orders/track")).content(track)).andExpect(status().isTooManyRequests());
    }
}
