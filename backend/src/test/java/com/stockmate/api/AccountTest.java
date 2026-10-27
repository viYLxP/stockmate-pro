package com.stockmate.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.stockmate.support.IntegrationTest;

/**
 * Shop customer accounts: sign-up, login by phone, their own orders only, and what the admin may change.
 * حسابات عملاء المتجر: التسجيل، والدخول بالجوال، وطلباتهم هم بس، ووش يقدر المدير يغيّر.
 */
class AccountTest extends IntegrationTest {

    long rice;

    @BeforeEach
    void catalogue() throws Exception {
        String admin = adminToken();
        rice = shopProduct(admin, "RICE", category(admin, "Food"), "40.00", 20);
    }

    private String order(String token, int quantity, String method) throws Exception {
        String body = mvc.perform(auth(post("/api/shop/checkout"), token).content(
                        checkoutJson("[{\"productId\":%d,\"quantity\":%d}]".formatted(rice, quantity),
                                "0500000000", method)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.number");
    }

    @Test
    void signUpThenLogInWithThePhoneTypedAnyWay() throws Exception {
        String token = registerCustomer("Nora", "+966 55 300 1010");
        mvc.perform(auth(get("/api/auth/me"), token))
                .andExpect(jsonPath("$.username").value("966553001010"))
                .andExpect(jsonPath("$.role").value("CUSTOMER"))
                .andExpect(jsonPath("$.phone").value("966553001010"));
        login("0553001010", CUSTOMER_PASSWORD);
        login("966553001010", CUSTOMER_PASSWORD);

        mvc.perform(json(post("/api/auth/register")).content("""
                        {"fullName":"Again","phone":"0553001010","password":"%s"}""".formatted(CUSTOMER_PASSWORD)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PHONE_REGISTERED"));
        mvc.perform(json(post("/api/auth/register")).content("""
                        {"fullName":"Bad","phone":"0123","password":"%s"}""".formatted(CUSTOMER_PASSWORD)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.phone").value("Phone"));
    }

    @Test
    void customersCannotOpenTheBackOffice() throws Exception {
        String token = registerCustomer("Nora", "0553001010");
        for (String url : new String[]{"/api/products", "/api/sales-orders", "/api/dashboard", "/api/users"}) {
            mvc.perform(auth(get(url), token)).andExpect(status().isForbidden());
        }
        // And staff have no customer account pages. / والموظفين ما لهم صفحات حساب عميل.
        mvc.perform(auth(get("/api/account/orders"), staffToken())).andExpect(status().isForbidden());
        mvc.perform(get("/api/account/orders")).andExpect(status().isUnauthorized());
    }

    @Test
    void aSignedInOrderUsesTheAccountAndIsVisibleOnlyToItsOwner() throws Exception {
        String nora = registerCustomer("Nora Al-Qahtani", "0553001010");
        String number = order(nora, 2, "CASH");
        // The account's name and phone win over the form. / اسم وجوال الحساب يغلبون النموذج.
        assertThat(jdbc.queryForObject("select ship_phone from sales_order where number = ?", String.class, number))
                .isEqualTo("966553001010");
        assertThat(jdbc.queryForObject("select ship_name from sales_order where number = ?", String.class, number))
                .isEqualTo("Nora Al-Qahtani");
        // The customer record in the office is linked to the account. / سجل العميل في المكتب مربوط بالحساب.
        assertThat(jdbc.queryForObject("select count(*) from customer where user_id is not null", Integer.class))
                .isEqualTo(1);
        order(nora, 1, "CASH");
        assertThat(jdbc.queryForObject("select count(*) from customer", Integer.class)).isEqualTo(1);

        mvc.perform(auth(get("/api/account/orders"), nora))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[1].number").value(number))
                .andExpect(jsonPath("$[1].itemCount").value(2));
        mvc.perform(auth(get("/api/account/orders/" + number), nora))
                .andExpect(jsonPath("$.lines[0].quantity").value(2))
                .andExpect(jsonPath("$.cancellable").value(true));

        String other = registerCustomer("Faisal", "0553001001");
        mvc.perform(auth(get("/api/account/orders"), other)).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(auth(get("/api/account/orders/" + number), other)).andExpect(status().isNotFound());
        mvc.perform(auth(post("/api/account/orders/" + number + "/cancel"), other)).andExpect(status().isNotFound());

        mvc.perform(auth(post("/api/account/orders/" + number + "/cancel"), nora))
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        assertThat(quantityOf(rice)).isEqualTo(19);
    }

    @Test
    void theCustomerPaysTheirOrderFromTheAccount() throws Exception {
        String nora = registerCustomer("Nora", "0553001010");
        String number = order(nora, 3, "APPLE_PAY");
        mvc.perform(auth(get("/api/account/orders"), nora)).andExpect(jsonPath("$[0].payable").value(true));
        String pay = mvc.perform(auth(post("/api/account/orders/" + number + "/pay"), nora)
                        .content("{\"method\":\"APPLE_PAY\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String paymentId = JsonPath.read(pay, "$.paymentId");
        // Pressing pay again returns the same open attempt. / الضغط على ادفع مرة ثانية يرجّع نفس المحاولة المفتوحة.
        assertThat(jdbc.queryForObject("select count(*) from payment", Integer.class)).isEqualTo(1);
        mvc.perform(json(post("/api/public/payments/" + paymentId + "/sandbox")).content("{\"approve\":true}"))
                .andExpect(jsonPath("$.status").value("PAID"))
                .andExpect(jsonPath("$.method").value("APPLE_PAY"));
        mvc.perform(auth(get("/api/account/orders/" + number), nora))
                .andExpect(jsonPath("$.payment.status").value("PAID"))
                .andExpect(jsonPath("$.payment.method").value("APPLE_PAY"));
        mvc.perform(auth(post("/api/account/orders/" + number + "/pay"), nora).content("{\"method\":\"CARD\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PAYMENT_NOT_ALLOWED"));
    }

    @Test
    void profileAndPassword() throws Exception {
        String nora = registerCustomer("Nora", "0553001010");
        mvc.perform(auth(put("/api/account/profile"), nora).content("{\"fullName\":\"Nora Q\",\"email\":\"n@x.sa\"}"))
                .andExpect(jsonPath("$.fullName").value("Nora Q"))
                .andExpect(jsonPath("$.email").value("n@x.sa"));
        mvc.perform(auth(put("/api/account/password"), nora)
                        .content("{\"currentPassword\":\"wrong-one\",\"newPassword\":\"NewPass@123\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("WRONG_PASSWORD"));
        mvc.perform(auth(put("/api/account/password"), nora)
                        .content("{\"currentPassword\":\"%s\",\"newPassword\":\"NewPass@123\"}".formatted(CUSTOMER_PASSWORD)))
                .andExpect(status().isNoContent());
        login("0553001010", "NewPass@123");
    }

    @Test
    void theAdminCanOnlySwitchACustomerOffOrOn() throws Exception {
        registerCustomer("Nora", "0553001010");
        String admin = adminToken();
        long id = jdbc.queryForObject("select id from app_user where role = 'CUSTOMER'", Long.class);
        mvc.perform(auth(get("/api/users"), admin))
                .andExpect(jsonPath("$[?(@.role == 'CUSTOMER')].phone").value(org.hamcrest.Matchers.contains("966553001010")));
        // Customers sign up themselves; the admin cannot create one, or turn one into staff.
        // العملاء يسجلون بنفسهم؛ والمدير ما يقدر ينشئ عميل، ولا يحوّله لموظف.
        mvc.perform(auth(post("/api/users"), admin).content("""
                        {"username":"cust","fullName":"C","password":"Passw0rd!","role":"CUSTOMER"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.role").value("Staff"));
        mvc.perform(auth(put("/api/users/" + id), admin).content("{\"fullName\":\"X\",\"role\":\"STAFF\",\"active\":true}"))
                .andExpect(status().isBadRequest());
        mvc.perform(auth(put("/api/users/" + id), admin).content("{\"fullName\":\"X\",\"role\":\"CUSTOMER\",\"active\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false))
                // The name is the customer's own. / الاسم ملك العميل.
                .andExpect(jsonPath("$.fullName").value("Nora"));
        mvc.perform(json(post("/api/auth/login")).content(
                        "{\"username\":\"0553001010\",\"password\":\"%s\"}".formatted(CUSTOMER_PASSWORD)))
                .andExpect(status().isUnauthorized());
    }
}
