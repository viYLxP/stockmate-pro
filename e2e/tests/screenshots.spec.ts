import { test } from '@playwright/test';

import { CUSTOMER, login } from './helpers';

// Captures README screenshots. Run with SCREENSHOTS=1; skipped otherwise so normal runs stay fast.
// يلتقط صور README. شغّله مع SCREENSHOTS=1؛ وإلا يتم تخطيه حتى يبقى التشغيل العادي سريع.
test.skip(!process.env['SCREENSHOTS'], 'set SCREENSHOTS=1 to capture README images');

for (const lang of ['ar', 'en'] as const) {
  test(`screenshots (${lang})`, async ({ page }, info) => {
    const dir = `../docs/screenshots/${info.project.name}`;
    await page.addInitScript((l) => localStorage.setItem('stockmate.lang', l), lang);
    await login(page);
    for (const [name, path] of [
      ['dashboard', '/dashboard'],
      ['products', '/products'],
      ['sales', '/sales'],
      ['movements', '/movements'],
    ] as const) {
      await page.goto(path);
      await page.locator('.loading').waitFor({ state: 'detached' }).catch(() => undefined);
      await page.waitForLoadState('networkidle');
      await page.screenshot({ path: `${dir}/${name}-${lang}.png`, fullPage: info.project.name === 'desktop' });
    }
  });
}

for (const lang of ['ar', 'en'] as const) {
  test(`shop screenshots (${lang})`, async ({ page }, info) => {
    const dir = `../docs/screenshots/${info.project.name}`;
    const full = info.project.name === 'desktop';
    const settle = async () => {
      await page.locator('.loading').waitFor({ state: 'detached' }).catch(() => undefined);
      await page.waitForLoadState('networkidle');
    };
    await page.addInitScript((l) => localStorage.setItem('stockmate.lang', l), lang);
    await page.goto('/shop');
    await settle();
    await page.screenshot({ path: `${dir}/shop-${lang}.png`, fullPage: full });
    await page.locator('[data-testid^="add-"]:enabled').first().click();
    await page.goto('/checkout');
    await settle();
    await page.getByTestId('co-method-TABBY').check({ force: true }).catch(() => undefined);
    // Back to the top, or the sticky header lands mid-page in a full-page shot. / نرجع للأعلى، وإلا الهيدر الثابت يطلع بنص الصورة.
    await page.evaluate(() => window.scrollTo(0, 0));
    await page.screenshot({ path: `${dir}/checkout-${lang}.png`, fullPage: full });

    await page.goto('/login');
    await page.locator('#username').fill(CUSTOMER.username);
    await page.locator('#password').fill(CUSTOMER.password);
    await page.locator('form button[type=submit]').click();
    await page.waitForURL(/\/account$/);
    await settle();
    await page.screenshot({ path: `${dir}/account-${lang}.png`, fullPage: full });
    await page.locator('[data-testid^="account-order-"] a').first().click();
    await settle();
    await page.screenshot({ path: `${dir}/account-order-${lang}.png`, fullPage: full });
  });
}
