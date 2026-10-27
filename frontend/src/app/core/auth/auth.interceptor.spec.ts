import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { User } from '../api/models';
import { authInterceptor } from './auth.interceptor';
import { AuthService } from './auth.service';

const user = (role: User['role']): User => ({
  id: 1,
  username: 'u',
  fullName: 'U',
  role,
  phone: null,
  email: null,
  active: true,
  createdAt: '2026-01-01T00:00:00Z',
});

describe('authInterceptor', () => {
  let http: HttpClient;
  let backend: HttpTestingController;

  beforeEach(() => {
    sessionStorage.setItem('stockmate.token', 'the-token');
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
      ],
    });
    http = TestBed.inject(HttpClient);
    backend = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    backend.verify();
    sessionStorage.clear();
  });

  it('sends the token to back-office endpoints', () => {
    http.get('/api/products').subscribe();
    expect(backend.expectOne('/api/products').request.headers.get('Authorization')).toBe(
      'Bearer the-token',
    );
  });

  it('never sends it to the payment page endpoints', () => {
    http.get('/api/public/payments/abc').subscribe();
    const req = backend.expectOne('/api/public/payments/abc');
    expect(req.request.headers.has('Authorization')).toBe(false);
    expect(req.request.headers.get('Accept-Language')).toBeTruthy();
  });

  it('sends it to the shop only for a signed-in customer', () => {
    const auth = TestBed.inject(AuthService);
    auth.updateUser(user('STAFF'));
    http.post('/api/shop/checkout', {}).subscribe();
    expect(backend.expectOne('/api/shop/checkout').request.headers.has('Authorization')).toBe(
      false,
    );

    auth.updateUser(user('CUSTOMER'));
    http.post('/api/shop/checkout', {}).subscribe();
    expect(backend.expectOne('/api/shop/checkout').request.headers.get('Authorization')).toBe(
      'Bearer the-token',
    );
  });
});
