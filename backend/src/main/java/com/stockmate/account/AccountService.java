package com.stockmate.account;

import java.util.List;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.stockmate.account.AccountDtos.PasswordChange;
import com.stockmate.account.AccountDtos.ProfileRequest;
import com.stockmate.account.AccountDtos.RegisterRequest;
import com.stockmate.common.ApiException;
import com.stockmate.common.ErrorCode;
import com.stockmate.common.Phones;
import com.stockmate.common.Text;
import com.stockmate.partner.CustomerRepository;
import com.stockmate.payment.Payment;
import com.stockmate.payment.PaymentDtos.CheckoutResponse;
import com.stockmate.shop.ShopDtos.OrderRow;
import com.stockmate.shop.ShopDtos.OrderView;
import com.stockmate.shop.ShopService;
import com.stockmate.user.AppUser;
import com.stockmate.user.AppUserRepository;
import com.stockmate.user.Role;
import com.stockmate.user.UserDtos.UserResponse;
import com.stockmate.user.UserService;

/**
 * The shop customer's own account: sign-up, profile, password, and their orders.
 * حساب عميل المتجر نفسه: التسجيل، والملف الشخصي، وكلمة المرور، وطلباته.
 *
 * <p>Every order lookup is by the signed-in account, so a customer can never open another customer's order,
 * even by guessing its number.</p>
 * <p>كل بحث عن طلب يكون بالحساب المسجّل، فالعميل ما يقدر يفتح طلب عميل ثاني أبدًا، حتى لو خمّن رقمه.</p>
 */
@Service
public class AccountService {

    private final AppUserRepository users;
    private final UserService userService;
    private final CustomerRepository customers;
    private final ShopService shop;
    private final PasswordEncoder passwordEncoder;

    public AccountService(AppUserRepository users, UserService userService, CustomerRepository customers,
                          ShopService shop, PasswordEncoder passwordEncoder) {
        this.users = users;
        this.userService = userService;
        this.customers = customers;
        this.shop = shop;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Creates a customer account. The normalised mobile number becomes the username.
     * ينشئ حساب عميل. رقم الجوال بالشكل الموحّد يصير اسم المستخدم.
     */
    @Transactional
    public AppUser register(RegisterRequest request) {
        String phone = Phones.require(request.phone(), "phone");
        if (users.existsByUsernameIgnoreCase(phone)) {
            throw new ApiException(ErrorCode.PHONE_REGISTERED);
        }
        AppUser user = new AppUser(phone, request.fullName().trim(), passwordEncoder.encode(request.password()),
                Role.CUSTOMER);
        user.setPhone(phone);
        user.setEmail(Text.blankToNull(request.email()));
        return users.save(user);
    }

    @Transactional
    public UserResponse updateProfile(ProfileRequest request) {
        AppUser me = userService.currentAccount();
        me.setFullName(request.fullName().trim());
        me.setEmail(Text.blankToNull(request.email()));
        // The office sees the new name on the customer record too. / المكتب يشوف الاسم الجديد في سجل العميل كمان.
        customers.findByUser(me).ifPresent(c -> c.applyContact(me.getFullName(), c.getPhone(),
                me.getEmail() != null ? me.getEmail() : c.getEmail(), c.getCity()));
        return UserResponse.from(me);
    }

    @Transactional
    public void changePassword(PasswordChange request) {
        AppUser me = userService.currentAccount();
        if (!passwordEncoder.matches(request.currentPassword(), me.getPasswordHash())) {
            throw new ApiException(ErrorCode.WRONG_PASSWORD);
        }
        me.setPasswordHash(passwordEncoder.encode(request.newPassword()));
    }

    @Transactional(readOnly = true)
    public List<OrderRow> orders() {
        return shop.ordersOf(userService.currentAccount());
    }

    @Transactional
    public OrderView order(String number) {
        return shop.orderOf(userService.currentAccount(), number);
    }

    @Transactional
    public CheckoutResponse pay(String number, Payment.Method method) {
        return shop.payAsCustomer(userService.currentAccount(), number, method);
    }

    @Transactional
    public OrderView cancel(String number) {
        return shop.cancelAsCustomer(userService.currentAccount(), number);
    }
}
