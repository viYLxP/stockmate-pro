import { expect, test } from '@playwright/test';

import { expectNoHorizontalScroll, login } from './helpers';

const PAGES = ['/dashboard', '/products', '/products/new', '/categories', '/suppliers', '/customers', '/purchases', '/sales', '/sales/new', '/movements', '/users'];

test('switching language keeps typed input and flips direction', async ({ page }) => {
  await login(page);
  await page.goto('/products/new');
  await page.locator('#name').fill('Basmati rice 5kg');
  await expect(page.locator('html')).toHaveAttribute('dir', 'rtl');

  await page.getByTestId('lang-switch').click();
  await expect(page.locator('html')).toHaveAttribute('dir', 'ltr');
  await expect(page.locator('html')).toHaveAttribute('lang', 'en');
  await expect(page.locator('#name')).toHaveValue('Basmati rice 5kg');

  // The choice survives a reload.
  // الاختيار يبقى بعد إعادة التحميل.
  await page.reload();
  await expect(page.locator('html')).toHaveAttribute('lang', 'en');
});

for (const lang of ['ar', 'en'] as const) {
  test(`every page renders without horizontal scroll or console errors (${lang})`, async ({ page }) => {
    const errors: string[] = [];
    page.on('console', (msg) => {
      if (msg.type() === 'error') errors.push(msg.text());
    });
    page.on('pageerror', (err) => errors.push(err.message));

    await page.addInitScript((l) => localStorage.setItem('stockmate.lang', l), lang);
    await login(page);
    for (const path of PAGES) {
      await page.goto(path);
      await expect(page.locator('h1').first()).toBeVisible();
      await expect(page.locator('.loading')).toHaveCount(0);
      await expectNoHorizontalScroll(page);
    }
    expect(errors).toEqual([]);
  });
}

test('the menu button only exists on small screens and opens the drawer', async ({ page }, info) => {
  await login(page);
  const menu = page.locator('.topbar__menu');
  if (info.project.name === 'desktop') {
    // Desktop shows the sidebar permanently, so the drawer buttons must stay hidden.
    // الكمبيوتر يعرض القائمة الجانبية دائمًا، فأزرار الدرج لازم تبقى مخفية.
    await expect(menu).toBeHidden();
    await expect(page.locator('.sidebar__close')).toBeHidden();
    await expect(page.locator('.sidebar')).toBeVisible();
  } else {
    await expect(page.locator('.sidebar')).toBeHidden();
    await menu.click();
    await expect(page.locator('.sidebar')).toBeVisible();
    // Navigating closes the drawer again.
    // التنقل يقفل الدرج مرة ثانية.
    await page.locator('.sidebar a[href="/products"]').click();
    await expect(page).toHaveURL(/\/products/);
    await expect(page.locator('.sidebar')).toBeHidden();
  }
});
