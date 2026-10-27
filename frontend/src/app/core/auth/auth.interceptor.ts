import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, throwError } from 'rxjs';

import { I18nService } from '../i18n/i18n.service';
import { AuthService } from './auth.service';

/**
 * Adds "Authorization: Bearer <token>" and "Accept-Language" to every API call, and logs the user out
 * when the server answers 401 (expired or invalid token).
 * يضيف "Authorization: Bearer <token>" و"Accept-Language" لكل طلب API، ويسجّل خروج المستخدم
 * لما يرد الخادم بـ 401 (رمز منتهي أو غير صالح).
 */
export const authInterceptor: HttpInterceptorFn = (request, next) => {
  const auth = inject(AuthService);
  const i18n = inject(I18nService);
  const token = auth.token();
  const isLogin = request.url.endsWith('/auth/login') || request.url.endsWith('/auth/register');
  // The payment page never needs a token. The shop gets one only from a customer (it files the order under the
  // account); a staff member browsing the shop is a visitor there, so an old staff token cannot break it.
  // صفحة الدفع ما تحتاج رمز أبدًا. والمتجر ياخذ رمز بس من العميل (عشان ينحفظ الطلب تحت حسابه)؛ والموظف اللي
  // يتصفح المتجر زائر هناك، فرمز الموظف القديم ما يخرّبه.
  const isPublic = request.url.startsWith('/api/public/');
  const isShop = request.url.startsWith('/api/shop/');
  const sendToken = !!token && !isLogin && !isPublic && (!isShop || auth.isCustomer());

  const headers: Record<string, string> = { 'Accept-Language': i18n.lang() };
  if (sendToken) {
    headers['Authorization'] = `Bearer ${token}`;
  }

  return next(request.clone({ setHeaders: headers })).pipe(
    catchError((error: unknown) => {
      // A 401 on login just means wrong credentials, which the login form shows itself.
      // 401 في تسجيل الدخول يعني بيانات خاطئة فقط، ونموذج الدخول يعرضها بنفسه.
      if (error instanceof HttpErrorResponse && error.status === 401 && sendToken) {
        auth.sessionExpired();
      }
      return throwError(() => error);
    }),
  );
};
