import { expect, test } from '@playwright/test';

import { ADMIN, STAFF, login } from './helpers';

test.describe('authentication', () => {
  test('rejects a wrong password, then signs in (Arabic, RTL)', async ({ page }) => {
    await page.goto('/');
    // Visitors land in the shop; the office is behind the sign-in page.
    // الزائر يوصل للمتجر؛ والمكتب ورا صفحة الدخول.
    await expect(page).toHaveURL(/\/shop$/);
    await page.goto('/dashboard');
    await expect(page).toHaveURL(/\/login/);
    await expect(page.locator('html')).toHaveAttribute('dir', 'rtl');
    await expect(page.locator('html')).toHaveAttribute('lang', 'ar');

    await page.locator('#username').fill(ADMIN.username);
    await page.locator('#password').fill('wrong-password');
    await page.locator('form button[type=submit]').click();
    await expect(page.getByTestId('login-error')).toBeVisible();

    await page.locator('#password').fill(ADMIN.password);
    await page.locator('form button[type=submit]').click();
    await expect(page).toHaveURL(/\/dashboard$/);
    await expect(page.locator('h1')).toBeVisible();
  });

  test('empty fields show validation messages without calling the server', async ({ page }) => {
    await page.goto('/login');
    let loginCalls = 0;
    page.on('request', (r) => {
      if (r.url().includes('/api/auth/login')) loginCalls++;
    });
    await page.locator('form button[type=submit]').click();
    await expect(page.locator('#username-error')).not.toBeEmpty();
    await expect(page.locator('#password-error')).not.toBeEmpty();
    expect(loginCalls).toBe(0);
  });

  test('staff cannot open user management', async ({ page }) => {
    await login(page, STAFF);
    await expect(page.locator('a[href="/users"]')).toHaveCount(0);
    await page.goto('/users');
    await expect(page).toHaveURL(/\/dashboard$/);
  });

  test('logout clears the session', async ({ page }) => {
    await login(page);
    await page.getByTestId('logout').click();
    await expect(page).toHaveURL(/\/login/);
    await page.goto('/products');
    await expect(page).toHaveURL(/\/login/);
  });
});
