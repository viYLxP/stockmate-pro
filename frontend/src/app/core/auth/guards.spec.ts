import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import {
  ActivatedRouteSnapshot,
  CanActivateFn,
  RouterStateSnapshot,
  UrlTree,
  provideRouter,
} from '@angular/router';

import { User } from '../api/models';
import { customerGuard, rootRedirect, staffGuard } from './guards';

const route = {} as ActivatedRouteSnapshot;
const state = (url: string) => ({ url }) as RouterStateSnapshot;

function setup(role: User['role'] | null) {
  sessionStorage.clear();
  if (role) sessionStorage.setItem('stockmate.token', 'token');
  TestBed.configureTestingModule({
    providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
  });
  const http = TestBed.inject(HttpTestingController);
  // Runs a guard and answers the session check it makes. / يشغّل الحارس ويرد على فحص الجلسة.
  return async (guard: CanActivateFn, url = '/') => {
    const result = TestBed.runInInjectionContext(() => guard(route, state(url))) as Promise<
      boolean | UrlTree
    >;
    if (role) {
      await Promise.resolve();
      http.match('/api/auth/me').forEach((r) => r.flush({ id: 1, username: 'u', role } as User));
    }
    const value = await result;
    return value instanceof UrlTree ? value.toString() : value;
  };
}

describe('guards', () => {
  it('sends visitors and customers to the shop, staff to the dashboard', async () => {
    expect(await setup(null)(rootRedirect)).toBe('/shop');
    TestBed.resetTestingModule();
    expect(await setup('CUSTOMER')(rootRedirect)).toBe('/shop');
    TestBed.resetTestingModule();
    expect(await setup('STAFF')(rootRedirect)).toBe('/dashboard');
  });

  it('keeps customers out of the office and visitors out of the account', async () => {
    expect(await setup('CUSTOMER')(staffGuard)).toBe('/account');
    TestBed.resetTestingModule();
    expect(await setup(null)(customerGuard, '/account/orders/SO-000001')).toBe(
      '/login?returnUrl=%2Faccount%2Forders%2FSO-000001',
    );
    TestBed.resetTestingModule();
    expect(await setup('ADMIN')(customerGuard, '/account')).toBe('/dashboard');
  });
});
