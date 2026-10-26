import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';

import { AuthService } from './auth.service';

/**
 * Lets the route open only for a logged-in user; otherwise redirects to /login.
 * يسمح بفتح الصفحة للمستخدم المسجّل فقط، وإلا يحوّله لصفحة الدخول.
 */
export const authGuard: CanActivateFn = async () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  return (await auth.restore()) ? true : router.createUrlTree(['/login']);
};

/**
 * Admin-only pages. This only hides the page in the UI; the real protection is on the server.
 * صفحات المدير فقط. هذا يخفي الصفحة في الواجهة فقط، والحماية الحقيقية في الخادم.
 */
export const adminGuard: CanActivateFn = async () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  await auth.restore();
  return auth.isAdmin() ? true : router.createUrlTree(['/dashboard']);
};

/**
 * Keeps logged-in users away from the login page.
 * يمنع المستخدم المسجّل من فتح صفحة الدخول مرة ثانية.
 */
export const guestGuard: CanActivateFn = async () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  return (await auth.restore()) ? router.createUrlTree(['/dashboard']) : true;
};
