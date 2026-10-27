import { SAUDI_MOBILE, localPhone, localized, normalizeOrderNumber, withVat } from './shop';

describe('shop helpers', () => {
  it('accepts Saudi mobiles typed the usual ways', () => {
    for (const ok of [
      '0551112233',
      '551112233',
      '+966 55 111 2233',
      '00966551112233',
      '055-111-2233',
    ]) {
      expect(SAUDI_MOBILE.test(ok)).toBe(true);
    }
    for (const bad of ['0451112233', '12345', '05511122334']) {
      expect(SAUDI_MOBILE.test(bad)).toBe(false);
    }
  });

  it('writes phones and order numbers the way people read them', () => {
    expect(localPhone('966551112233')).toBe('0551112233');
    expect(localPhone(null)).toBe('');
    expect(normalizeOrderNumber(' so-17 ')).toBe('SO-000017');
    expect(normalizeOrderNumber('SO-000123')).toBe('SO-000123');
  });

  it('falls back to English when there is no Arabic text', () => {
    expect(localized('ar', 'Rice', 'أرز')).toBe('أرز');
    expect(localized('ar', 'Rice', null)).toBe('Rice');
    expect(localized('en', 'Rice', 'أرز')).toBe('Rice');
  });
});

describe('withVat', () => {
  it('adds 15% and rounds to the halala', () => {
    expect(withVat(19)).toBe(21.85);
    expect(withVat(0.1)).toBe(0.12);
    expect(withVat(100)).toBe(115);
  });
});
