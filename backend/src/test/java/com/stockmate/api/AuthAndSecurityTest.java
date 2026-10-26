package com.stockmate.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import com.stockmate.support.IntegrationTest;

/**
 * Login, token handling and role-based access rules.
 * اختبارات تسجيل الدخول، والتعامل مع الرموز، وقواعد الصلاحيات حسب الدور.
 */
class AuthAndSecurityTest extends IntegrationTest {

    @Test
    void loginReturnsTokenAndUserWithoutPasswordHash() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"ADMIN\",\"password\":\"%s\"}".formatted(ADMIN_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.user.role").value("ADMIN"))
                // The hash must never leak into any response.
                // ناتج التشفير لازم ما يطلع في أي رد أبدًا.
                .andExpect(jsonPath("$.user.passwordHash").doesNotExist());
    }

    @Test
    void wrongPasswordAndUnknownUserGetTheSameError() throws Exception {
        // Same 401 + code for both cases, so an attacker cannot tell which usernames exist.
        // نفس 401 ونفس الرمز للحالتين، حتى ما يعرف المهاجم أي أسماء مستخدمين موجودة.
        for (String body : new String[]{"{\"username\":\"admin\",\"password\":\"wrong-pass\"}",
                "{\"username\":\"nobody\",\"password\":\"whatever1\"}"}) {
            mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("BAD_CREDENTIALS"));
        }
    }

    @Test
    void disabledUserCannotLogIn() throws Exception {
        jdbc.update("update app_user set active = false where username = 'staff'");
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"staff\",\"password\":\"%s\"}".formatted(STAFF_PASSWORD)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("BAD_CREDENTIALS"));
    }

    @Test
    void requestsWithoutOrWithTamperedTokenAreRejected() throws Exception {
        mvc.perform(get("/api/products")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        // Changing one character breaks the HMAC signature.
        // تغيير حرف واحد يكسر توقيع HMAC.
        String token = staffToken();
        String tampered = token.substring(0, token.length() - 2) + (token.endsWith("A") ? "BB" : "AA");
        mvc.perform(get("/api/products").header("Authorization", "Bearer " + tampered))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void meReturnsTheLoggedInUser() throws Exception {
        mvc.perform(auth(get("/api/auth/me"), staffToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("staff"))
                .andExpect(jsonPath("$.role").value("STAFF"));
    }

    @Test
    void staffCannotManageUsersOrDelete() throws Exception {
        String staff = staffToken();
        long categoryId = category(staff, "Tools");
        mvc.perform(auth(get("/api/users"), staff)).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mvc.perform(auth(delete("/api/categories/" + categoryId), staff)).andExpect(status().isForbidden());
        // Admin can delete the same (empty) category.
        // المدير يقدر يحذف نفس التصنيف (الفاضي).
        mvc.perform(auth(delete("/api/categories/" + categoryId), adminToken())).andExpect(status().isNoContent());
    }

    @Test
    void adminManagesUsersButCannotDisableThemself() throws Exception {
        String admin = adminToken();
        long userId = createId("/api/users",
                "{\"username\":\"sara\",\"fullName\":\"Sara\",\"password\":\"Password1\",\"role\":\"STAFF\"}", admin);
        // Duplicate username, compared case-insensitively.
        // اسم مستخدم مكرر، والمقارنة بدون حساسية للأحرف.
        mvc.perform(auth(post("/api/users"), admin)
                        .content("{\"username\":\"SARA\",\"fullName\":\"Other\",\"password\":\"Password1\",\"role\":\"STAFF\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("USERNAME_EXISTS"));
        // The new user can log in.
        // المستخدم الجديد يقدر يدخل.
        login("sara", "Password1");

        long adminId = jdbc.queryForObject("select id from app_user where username = 'admin'", Long.class);
        mvc.perform(auth(put("/api/users/" + adminId), admin)
                        .content("{\"fullName\":\"Admin\",\"role\":\"ADMIN\",\"active\":false}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CANNOT_MODIFY_SELF"));
        mvc.perform(auth(put("/api/users/" + userId), admin)
                        .content("{\"fullName\":\"Sara A.\",\"role\":\"STAFF\",\"active\":false}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.active").value(false));
    }

    @Test
    void weakPasswordIsRejectedWithFieldError() throws Exception {
        mvc.perform(auth(post("/api/users"), adminToken())
                        .content("{\"username\":\"bad name\",\"fullName\":\"X\",\"password\":\"123\",\"role\":\"STAFF\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fields.password").value("Size"))
                .andExpect(jsonPath("$.fields.username").value("Pattern"));
    }
}
