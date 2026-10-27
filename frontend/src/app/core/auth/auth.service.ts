import { Injectable, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Observable, firstValueFrom, tap } from 'rxjs';

import { ApiService } from '../api/api.service';
import { LoginResponse, User } from '../api/models';

const TOKEN_KEY = 'stockmate.token';

/**
 * Session state: the JWT and the logged-in user.
 * حالة الجلسة: رمز JWT والمستخدم المسجّل.
 *
 * Security note: the token is kept in sessionStorage (cleared when the tab closes) rather than localStorage,
 * which narrows how long a stolen token could be reused. The server also expires tokens (8h by default).
 * ملاحظة أمنية: الرمز محفوظ في sessionStorage (ينمسح لما تقفل التبويب) بدل localStorage،
 * وهذا يقلل مدة إمكانية استخدام رمز مسروق. والخادم ينهي صلاحية الرمز أيضًا (8 ساعات افتراضيًا).
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly api = inject(ApiService);
  private readonly router = inject(Router);

  private readonly tokenSignal = signal<string | null>(readToken());
  private readonly userSignal = signal<User | null>(null);

  readonly token = this.tokenSignal.asReadonly();
  readonly user = this.userSignal.asReadonly();
  readonly isAdmin = computed(() => this.userSignal()?.role === 'ADMIN');
  // Shop customers who signed up themselves; they never enter the back office.
  // عملاء المتجر اللي سجلوا بنفسهم؛ وما يدخلون لوحة المكتب أبدًا.
  readonly isCustomer = computed(() => this.userSignal()?.role === 'CUSTOMER');
  readonly isStaff = computed(() => !!this.userSignal() && !this.isCustomer());
  // Where each role lands after signing in. / الصفحة اللي يوصل لها كل دور بعد الدخول.
  readonly home = computed(() => (this.isCustomer() ? '/account' : '/dashboard'));

  // Staff sign in with a username, customers with their mobile number; the server tells them apart.
  // الموظف يدخل باسم المستخدم، والعميل برقم جواله؛ والخادم يفرّق بينهم.
  login(username: string, password: string): Observable<LoginResponse> {
    return this.api
      .post<LoginResponse>('/auth/login', { username, password })
      .pipe(tap((response) => this.start(response)));
  }

  /** Creates a customer account and signs it in. / ينشئ حساب عميل ويسجّل دخوله. */
  register(body: {
    fullName: string;
    phone: string;
    email: string | null;
    password: string;
  }): Observable<LoginResponse> {
    return this.api
      .post<LoginResponse>('/auth/register', body)
      .pipe(tap((response) => this.start(response)));
  }

  /** Keeps the page's copy of the user in step after a profile change. / يحدّث نسخة المستخدم بعد تعديل الملف. */
  updateUser(user: User): void {
    this.userSignal.set(user);
  }

  /**
   * Restores the session after a page reload: if a token is saved, ask the server who it belongs to.
   * An expired or invalid token fails here and the user is simply treated as logged out.
   * يسترجع الجلسة بعد إعادة تحميل الصفحة: لو فيه رمز محفوظ، نسأل الخادم لمن هو.
   * الرمز المنتهي أو غير الصالح يفشل هنا، ونعامل المستخدم كأنه غير مسجّل.
   */
  async restore(): Promise<boolean> {
    if (this.userSignal()) {
      return true;
    }
    if (!this.tokenSignal()) {
      return false;
    }
    try {
      this.userSignal.set(await firstValueFrom(this.api.get<User>('/auth/me')));
      return true;
    } catch {
      this.clear();
      return false;
    }
  }

  // Customers go back to the shop; staff to the sign-in page.
  // العميل يرجع للمتجر؛ والموظف لصفحة الدخول.
  logout(): void {
    const target = this.isCustomer() ? '/shop' : '/login';
    this.clear();
    void this.router.navigateByUrl(target);
  }

  /** Called by the interceptor on any 401 from the API. / يستدعيه الـ interceptor عند أي 401 من الـ API. */
  sessionExpired(): void {
    this.clear();
    void this.router.navigate(['/login'], { queryParams: { expired: 1 } });
  }

  private start(response: LoginResponse): void {
    this.storeToken(response.token);
    this.userSignal.set(response.user);
  }

  private storeToken(token: string): void {
    this.tokenSignal.set(token);
    try {
      sessionStorage.setItem(TOKEN_KEY, token);
    } catch {
      // Storage blocked: the session still works until the page is reloaded.
      // التخزين محجوب: الجلسة تشتغل لين تتحدث الصفحة.
    }
  }

  private clear(): void {
    this.tokenSignal.set(null);
    this.userSignal.set(null);
    try {
      sessionStorage.removeItem(TOKEN_KEY);
    } catch {
      // Nothing to clean up if storage is unavailable.
      // ما فيه شي ننظفه لو التخزين غير متاح.
    }
  }
}

function readToken(): string | null {
  try {
    return sessionStorage.getItem(TOKEN_KEY);
  } catch {
    return null;
  }
}
