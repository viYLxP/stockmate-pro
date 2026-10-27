package com.stockmate.security;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.stockmate.config.AppProperties;

/**
 * Limits the public POST endpoints per visitor IP: new orders and sign-ups (spam, holding stock), and order
 * lookups and payment attempts (guessing phone numbers or card numbers).
 * يحدّ روابط POST العامة لكل عنوان IP: الطلبات الجديدة والتسجيل (الإزعاج وحجز المخزون)، وتتبع الطلبات ومحاولات
 * الدفع (تخمين أرقام الجوال أو البطاقات).
 *
 * <p>The IP comes from request.getRemoteAddr(). With server.forward-headers-strategy=native, Tomcat replaces it with
 * the X-Forwarded-For value only when the request came through a trusted internal proxy (our nginx).</p>
 * <p>العنوان من request.getRemoteAddr(). ومع server.forward-headers-strategy=native، Tomcat يبدله بقيمة
 * X-Forwarded-For بس لو الطلب جا من بروكسي داخلي موثوق (nginx حقنا).</p>
 */
@Component
public class PublicRateLimitFilter extends OncePerRequestFilter {

    private static final String CHECKOUT_PATH = "/api/shop/checkout";
    private static final String REGISTER_PATH = "/api/auth/register";
    private static final String ORDERS_PREFIX = "/api/shop/orders/";
    private static final String PAYMENTS_PREFIX = "/api/public/payments/";

    private final RateLimiter creates;
    private final RateLimiter lookups;

    public PublicRateLimitFilter(AppProperties properties) {
        AppProperties.RateLimit config = properties.rateLimit();
        this.creates = new RateLimiter(config.orders(), config.window(), System::currentTimeMillis);
        this.lookups = new RateLimiter(config.lookups(), config.window(), System::currentTimeMillis);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !HttpMethod.POST.matches(request.getMethod()) || !(path.equals(CHECKOUT_PATH)
                || path.equals(REGISTER_PATH) || path.startsWith(ORDERS_PREFIX) || path.startsWith(PAYMENTS_PREFIX));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getRequestURI();
        RateLimiter limiter = path.startsWith(ORDERS_PREFIX) || path.startsWith(PAYMENTS_PREFIX) ? lookups : creates;
        long retryAfter = limiter.hit(request.getRemoteAddr());
        if (retryAfter > 0) {
            response.setStatus(429);
            response.setHeader("Retry-After", Long.toString(retryAfter));
            response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
            response.getWriter().write("{\"status\":429,\"title\":\"Too Many Requests\",\"code\":\"RATE_LIMITED\","
                    + "\"retryAfterSeconds\":" + retryAfter + "}");
            return;
        }
        chain.doFilter(request, response);
    }
}
