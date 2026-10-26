package com.stockmate.security;

import java.io.IOException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

/**
 * Writes a JSON 403 response when a logged-in user lacks the required role at URL level.
 * يكتب رد 403 بصيغة JSON عندما لا يملك المستخدم المسجّل الصلاحية المطلوبة على مستوى الرابط.
 */
@Component
public class ProblemAccessDeniedHandler implements AccessDeniedHandler {

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.getWriter().write("{\"status\":403,\"title\":\"Forbidden\",\"code\":\"FORBIDDEN\"}");
    }
}
