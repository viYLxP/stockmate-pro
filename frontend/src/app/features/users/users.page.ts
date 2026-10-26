import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  computed,
  inject,
  signal,
  viewChild,
} from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Observable } from 'rxjs';

import { ApiError, errorMessage, toApiError } from '../../core/api/api-error';
import { ApiService } from '../../core/api/api.service';
import { Role, User } from '../../core/api/models';
import { AuthService } from '../../core/auth/auth.service';
import { FormatService } from '../../core/i18n/format.service';
import { I18nService } from '../../core/i18n/i18n.service';
import { TranslatePipe } from '../../core/i18n/translate.pipe';
import { FieldErrorComponent } from '../../core/ui/field-error.component';
import { IconComponent } from '../../core/ui/icon.component';
import { ToastService } from '../../core/ui/toast.service';

// The single dialog has three modes; each mode shows only the fields the matching API call accepts.
// النافذة الوحدة لها ثلاث حالات؛ كل حالة تعرض فقط الحقول اللي يقبلها طلب الـ API المقابل.
type DialogMode = 'create' | 'edit' | 'password';

/**
 * Admin-only user management: create accounts, change role/active state and reset passwords.
 * إدارة المستخدمين للمدير فقط: إنشاء حسابات، تغيير الدور والحالة، وإعادة تعيين كلمة المرور.
 *
 * Users are never deleted: their name is kept on orders and stock movements, so we deactivate instead.
 * المستخدمون لا يُحذفون أبدًا: أسماؤهم محفوظة على الطلبات وحركات المخزون، فنعطّل الحساب بدل الحذف.
 */
@Component({
  selector: 'app-users',
  imports: [ReactiveFormsModule, TranslatePipe, IconComponent, FieldErrorComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './users.page.html',
})
export class UsersPage {
  private readonly api = inject(ApiService);
  private readonly i18n = inject(I18nService);
  private readonly toast = inject(ToastService);
  protected readonly auth = inject(AuthService);
  protected readonly fmt = inject(FormatService);
  private readonly dialog = viewChild.required<ElementRef<HTMLDialogElement>>('dialog');

  protected readonly users = rxResource({ stream: () => this.api.get<User[]>('/users') });
  protected readonly roles: Role[] = ['ADMIN', 'STAFF'];

  protected readonly mode = signal<DialogMode>('create');
  protected readonly target = signal<User | null>(null);
  protected readonly saving = signal(false);
  private readonly apiError = signal<ApiError | null>(null);
  protected readonly errorText = computed(() => {
    const err = this.apiError();
    return err && err.code !== 'VALIDATION_FAILED' ? errorMessage(this.i18n, err) : null;
  });
  protected readonly serverFields = computed(() => this.apiError()?.fields ?? {});

  // Editing yourself: role and active are locked in the UI, mirroring the server's CANNOT_MODIFY_SELF rule.
  // تعديل نفسك: الدور والحالة مقفلة في الواجهة، مطابقة لقاعدة CANNOT_MODIFY_SELF في الخادم.
  protected readonly editingSelf = computed(() => this.isSelf(this.target()));

  protected readonly form = inject(NonNullableFormBuilder).group({
    username: [
      '',
      [
        Validators.required,
        Validators.minLength(3),
        Validators.maxLength(50),
        Validators.pattern(/^[A-Za-z0-9._-]+$/),
      ],
    ],
    fullName: ['', [Validators.required, Validators.pattern(/\S/), Validators.maxLength(120)]],
    // Same bounds as the server: BCrypt only uses the first 72 bytes.
    // نفس حدود الخادم: BCrypt يستخدم أول 72 بايت فقط.
    password: ['', [Validators.required, Validators.minLength(8), Validators.maxLength(72)]],
    role: ['STAFF' as Role, Validators.required],
    active: [true],
  });

  protected isSelf(user: User | null): boolean {
    const me = this.auth.user();
    return !!user && !!me && user.username.toLowerCase() === me.username.toLowerCase();
  }

  protected open(mode: DialogMode, user: User | null): void {
    this.mode.set(mode);
    this.target.set(user);
    this.apiError.set(null);
    this.form.reset({
      username: user?.username ?? '',
      fullName: user?.fullName ?? '',
      password: '',
      role: user?.role ?? 'STAFF',
      active: user?.active ?? true,
    });
    // Disabled controls are skipped by validation, so each mode enables only the fields it submits.
    // الحقول المعطّلة تُتجاهل في التحقق، فكل حالة تفعّل فقط الحقول اللي ترسلها.
    const c = this.form.controls;
    this.toggle(c.username, mode === 'create');
    this.toggle(c.password, mode !== 'edit');
    this.toggle(c.fullName, mode !== 'password');
    this.toggle(c.role, mode !== 'password' && !this.isSelf(user));
    this.toggle(c.active, mode === 'edit' && !this.isSelf(user));
    this.dialog().nativeElement.showModal();
  }

  private toggle(control: { enable(): void; disable(): void }, on: boolean): void {
    if (on) control.enable();
    else control.disable();
  }

  protected close(): void {
    this.dialog().nativeElement.close();
  }

  protected submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    // getRawValue includes disabled controls, so locked role/active keep the user's current values.
    // getRawValue يشمل الحقول المعطّلة، فالدور والحالة المقفلة تحتفظ بقيم المستخدم الحالية.
    const v = this.form.getRawValue();
    const user = this.target();
    const mode = this.mode();
    const request: Observable<unknown> =
      mode === 'create'
        ? this.api.post<User>('/users', {
            username: v.username.trim(),
            fullName: v.fullName.trim(),
            password: v.password,
            role: v.role,
          })
        : mode === 'edit'
          ? this.api.put<User>(`/users/${user!.id}`, {
              fullName: v.fullName.trim(),
              role: v.role,
              active: v.active,
            })
          : this.api.put<void>(`/users/${user!.id}/password`, { password: v.password });
    this.saving.set(true);
    request.subscribe({
      next: () => {
        this.saving.set(false);
        this.toast.success(
          this.i18n.t(mode === 'password' ? 'users.passwordReset' : 'common.saved'),
        );
        this.close();
        if (mode !== 'password') this.users.reload();
      },
      error: (err) => {
        this.saving.set(false);
        this.apiError.set(toApiError(err));
      },
    });
  }
}
