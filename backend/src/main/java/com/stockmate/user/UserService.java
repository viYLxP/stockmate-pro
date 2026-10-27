package com.stockmate.user;

import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.stockmate.common.ApiException;
import com.stockmate.common.CurrentUser;
import com.stockmate.common.ErrorCode;
import com.stockmate.user.UserDtos.CreateUserRequest;
import com.stockmate.user.UserDtos.UpdateUserRequest;
import com.stockmate.user.UserDtos.UserResponse;

/**
 * Business rules for managing user accounts (admin only).
 * قواعد إدارة حسابات المستخدمين (للمدير فقط).
 */
@Service
public class UserService {

    private final AppUserRepository users;
    private final PasswordEncoder passwordEncoder;

    public UserService(AppUserRepository users, PasswordEncoder passwordEncoder) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public List<UserResponse> list() {
        return users.findAll(Sort.by("username")).stream().map(UserResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public UserResponse current() {
        return UserResponse.from(findByUsername(CurrentUser.username()));
    }

    /**
     * The signed-in account itself, for the customer's own pages.
     * الحساب المسجّل دخول نفسه، لصفحات العميل.
     */
    public AppUser currentAccount() {
        return findByUsername(CurrentUser.username());
    }

    @Transactional
    public UserResponse create(CreateUserRequest request) {
        // Customers sign up themselves on the shop; the admin creates staff only.
        // العملاء يسجلون بنفسهم في المتجر؛ والمدير ينشئ موظفين بس.
        if (request.role() == Role.CUSTOMER) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, Map.of("fields", Map.of("role", "Staff")));
        }
        // Check first to return a clear error code instead of a raw database constraint error.
        // نتحقق أولًا حتى نرجّع رمز خطأ واضح بدل خطأ قيد قاعدة البيانات.
        if (users.existsByUsernameIgnoreCase(request.username())) {
            throw new ApiException(ErrorCode.USERNAME_EXISTS);
        }
        AppUser user = new AppUser(request.username().trim(), request.fullName().trim(),
                passwordEncoder.encode(request.password()), request.role());
        return UserResponse.from(users.save(user));
    }

    @Transactional
    public UserResponse update(Long id, UpdateUserRequest request) {
        AppUser user = users.findById(id).orElseThrow(() -> ApiException.notFound("user", id));
        // An admin cannot disable or demote themselves; otherwise they could lock everyone out.
        // المدير لا يقدر يوقف نفسه أو ينزّل صلاحيته، وإلا ممكن يقفل النظام على الجميع.
        boolean isSelf = user.getUsername().equalsIgnoreCase(CurrentUser.username());
        if (isSelf && (!request.active() || request.role() != user.getRole())) {
            throw new ApiException(ErrorCode.CANNOT_MODIFY_SELF);
        }
        // A customer stays a customer and staff stay staff: the two kinds of account are never swapped.
        // العميل يبقى عميل والموظف يبقى موظف: نوعين الحسابات ما يتبادلون أبدًا.
        if ((user.getRole() == Role.CUSTOMER) != (request.role() == Role.CUSTOMER)) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, Map.of("fields", Map.of("role", "Staff")));
        }
        if (user.getRole() == Role.CUSTOMER) {
            // For customers the admin only switches the account on or off; their profile is theirs.
            // للعملاء المدير بس يشغّل أو يوقف الحساب؛ وملفهم الشخصي حقهم.
            user.setActive(request.active());
            return UserResponse.from(user);
        }
        user.setFullName(request.fullName().trim());
        user.setRole(request.role());
        user.setActive(request.active());
        // No save() call needed: the entity is managed, and JPA writes changes when the transaction commits.
        // لا نحتاج save(): الكيان مُدار، وJPA يحفظ التغييرات تلقائيًا عند نهاية المعاملة.
        return UserResponse.from(user);
    }

    @Transactional
    public void resetPassword(Long id, String newPassword) {
        AppUser user = users.findById(id).orElseThrow(() -> ApiException.notFound("user", id));
        user.setPasswordHash(passwordEncoder.encode(newPassword));
    }

    private AppUser findByUsername(String username) {
        return users.findByUsernameIgnoreCase(username)
                .orElseThrow(() -> ApiException.notFound("user", username));
    }
}
