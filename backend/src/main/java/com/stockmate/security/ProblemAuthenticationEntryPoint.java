package com.stockmate.security;

import java.io.IOException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

/**
 * Writes a JSON 401 response when a request has no token, or an invalid/expired one.
 * يكتب رد 401 بصيغة JSON عندما يكون الطلب بدون رمز، أو برمز غير صالح أو منتهي.
 *
 * <p>Security filters run before controllers, so the global exception handler cannot catch these
 * errors; we write the same ProblemDetail shape by hand here.</p>
 * <p>فلاتر الأمان تعمل قبل الـ Controllers، فلا يصلها GlobalExceptionHandler؛ لذلك نكتب نفس
 * شكل ProblemDetail يدويًا هنا.</p>
 */
@Component
public class ProblemAuthenticationEntryPoint implements AuthenticationEntryPoint {

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        // UNAUTHENTICATED tells the UI to send the user back to the login page.
        // الرمز UNAUTHENTICATED يخبر الواجهة أن ترجع المستخدم لصفحة الدخول.
        response.getWriter().write("{\"status\":401,\"title\":\"Unauthorized\",\"code\":\"UNAUTHENTICATED\"}");
    }
}
