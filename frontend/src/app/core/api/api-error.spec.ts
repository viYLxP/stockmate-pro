import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';

import { I18nService } from '../i18n/i18n.service';
import { errorMessage, toApiError } from './api-error';

describe('api-error', () => {
  let i18n: I18nService;

  beforeEach(() => {
    i18n = TestBed.inject(I18nService);
    i18n.setLang('en');
  });

  it('reads code, fields and details from a ProblemDetail body', () => {
    const err = toApiError(
      new HttpErrorResponse({
        status: 400,
        error: { code: 'VALIDATION_FAILED', fields: { sku: 'Pattern' } },
      }),
    );
    expect(err.status).toBe(400);
    expect(err.code).toBe('VALIDATION_FAILED');
    expect(err.fields).toEqual({ sku: 'Pattern' });
  });

  it('maps status 0 to NETWORK and non-HTTP errors to UNKNOWN', () => {
    expect(toApiError(new HttpErrorResponse({ status: 0 })).code).toBe('NETWORK');
    expect(toApiError(new Error('boom')).code).toBe('UNKNOWN');
    // A proxy HTML error page has no JSON code.
    // صفحة خطأ HTML من البروكسي ما فيها code بصيغة JSON.
    expect(toApiError(new HttpErrorResponse({ status: 502, error: '<html>' })).code).toBe(
      'UNKNOWN',
    );
  });

  it('fills details into the translated message', () => {
    const err = toApiError(
      new HttpErrorResponse({
        status: 409,
        error: { code: 'INSUFFICIENT_STOCK', sku: 'RICE-5', available: 2, requested: 5 },
      }),
    );
    const message = errorMessage(i18n, err);
    expect(message).toContain('RICE-5');
    expect(message).toContain('2');
    expect(message).toContain('5');
  });

  it('translates an order status inside the message', () => {
    const err = toApiError(
      new HttpErrorResponse({ status: 409, error: { code: 'INVALID_STATUS', status: 'RECEIVED' } }),
    );
    expect(errorMessage(i18n, err)).toContain(i18n.t('orderStatus.RECEIVED'));
  });

  it('falls back to a generic message for unknown codes', () => {
    const err = toApiError(
      new HttpErrorResponse({ status: 418, error: { code: 'SOMETHING_NEW' } }),
    );
    expect(errorMessage(i18n, err)).toBe(i18n.t('errors.UNKNOWN'));
  });
});
