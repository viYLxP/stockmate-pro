import { Page, expect } from '@playwright/test';

// Demo accounts created by the seeder (passwords come from .env; these are the documented defaults).
// الحسابات التجريبية اللي ينشئها الـ seeder (كلمات المرور من .env، وهذي القيم الافتراضية الموثقة).
export const ADMIN = { username: 'admin', password: process.env['ADMIN_PASSWORD'] ?? 'Admin@12345' };
export const STAFF = { username: 'staff', password: process.env['STAFF_PASSWORD'] ?? 'Staff@12345' };

// Seeded shop customer (see DemoDataSeeder). / عميل المتجر التجريبي (شوف DemoDataSeeder).
export const CUSTOMER = {
  username: '0553001010',
  password: process.env['CUSTOMER_PASSWORD'] ?? 'Customer@12345',
};

export async function login(page: Page, user = ADMIN): Promise<void> {
  await page.goto('/login');
  await page.locator('#username').fill(user.username);
  await page.locator('#password').fill(user.password);
  await page.locator('form button[type=submit]').click();
  await expect(page).toHaveURL(/\/dashboard$/);
}

/**
 * Asserts the page has no horizontal scroll, the most common RTL/mobile layout bug.
 * يتأكد إن الصفحة ما فيها تمرير أفقي، وهو أشهر خلل في التصميم مع RTL والجوال.
 */
export async function expectNoHorizontalScroll(page: Page): Promise<void> {
  // Compare with screen.width, not innerWidth: a mobile browser widens the layout viewport (and innerWidth)
  // to fit overflowing content, which would hide the bug.
  // نقارن مع screen.width وليس innerWidth: متصفح الجوال يوسّع منطقة العرض (و innerWidth) لتناسب المحتوى الفائض،
  // وهذا كان بيخفي الخلل.
  const overflow = await page.evaluate(
    () => document.documentElement.scrollWidth - Math.min(window.innerWidth, window.screen.width),
  );
  expect(overflow, 'page is wider than the viewport').toBeLessThanOrEqual(1);
}

// Unique suffix so repeated runs never collide on SKU or names.
// لاحقة فريدة حتى التشغيل المتكرر ما يتعارض في رمز المنتج أو الأسماء.
export const unique = () => Date.now().toString(36).toUpperCase();

/** A fresh Saudi mobile number per run, so guest orders and sign-ups never collide. / رقم جوال جديد لكل تشغيل. */
export function uniquePhone(): string {
  return `05${String(Date.now() % 100_000_000).padStart(8, '0')}`;
}
