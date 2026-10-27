import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';

import { AuthService } from './auth.service';

// Guards only hide pages in the UI; the real protection is the server's role check on every endpoint.
// الحراس يخفون الصفحات في الواجهة فقط؛ والحماية الحقيقية هي فحص الدور في الخادم على كل رابط.

/**
 * The back office: staff only. A visitor goes to the sign-in page, a signed-in customer to their account.
 * لوحة المكتب: للموظفين بس. الزائر يروح لصفحة الدخول، والعميل المسجّل لحسابه.
 */
export const staffGuard: CanActivateFn = async () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  if (!(await auth.restore())) return router.createUrlTree(['/login']);
  return auth.isStaff() ? true : router.parseUrl(auth.home());
};

/**
 * Admin-only pages.
 * صفحات المدير فقط.
 */
export const adminGuard: CanActivateFn = async () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  await auth.restore();
  return auth.isAdmin() ? true : router.createUrlTree(['/dashboard']);
};

/**
 * Keeps logged-in users away from the login and sign-up pages.
 * يمنع المستخدم المسجّل من فتح صفحة الدخول والتسجيل مرة ثانية.
 */
export const guestGuard: CanActivateFn = async () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  return (await auth.restore()) ? router.parseUrl(auth.home()) : true;
};

/**
 * Customer pages (/account): a visitor is asked to sign in and comes back to the same page afterwards.
 * صفحات العميل (‎/account): الزائر ينطلب منه يسجّل دخول ويرجع لنفس الصفحة بعدها.
 */
export const customerGuard: CanActivateFn = async (_route, state) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  if (!(await auth.restore())) {
    return router.createUrlTree(['/login'], { queryParams: { returnUrl: state.url } });
  }
  return auth.isCustomer() ? true : router.parseUrl(auth.home());
};

/**
 * The site root: visitors and customers see the shop, staff their dashboard.
 * جذر الموقع: الزوار والعملاء يشوفون المتجر، والموظفين لوحتهم.
 */
export const rootRedirect: CanActivateFn = async () => {
  // inject() only works before the first await. / inject() يشتغل بس قبل أول await.
  const auth = inject(AuthService);
  const router = inject(Router);
  await auth.restore();
  return router.parseUrl(auth.isStaff() ? '/dashboard' : '/shop');
};

/**
 * Shop pages restore a saved session first, so the header knows who is signed in.
 * صفحات المتجر تسترجع الجلسة المحفوظة أول، حتى الهيدر يعرف مين داخل.
 */
export const sessionRestore: CanActivateFn = async () => {
  await inject(AuthService).restore();
  return true;
};
