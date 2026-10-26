import { Page, expect, test } from '@playwright/test';

import { login, unique } from './helpers';

/**
 * The core business path: create a product with opening stock, try to oversell it (must fail),
 * then sell what is available and check stock and totals.
 * المسار الأساسي للنشاط: إنشاء منتج بمخزون افتتاحي، محاولة بيع أكثر من المتوفر (لازم تفشل)،
 * ثم بيع المتوفر والتأكد من المخزون والإجماليات.
 */
async function createProduct(page: Page, sku: string): Promise<string> {
  await page.goto('/products/new');
  await page.locator('#sku').fill(sku);
  await page.locator('#name').fill(`E2E product ${sku}`);
  await page.locator('#categoryId').selectOption({ index: 1 });
  await page.locator('#unit').fill('pcs');
  await page.locator('#costPrice').fill('10.00');
  await page.locator('#salePrice').fill('15.00');
  await page.locator('#openingQuantity').fill('3');
  await page.getByTestId('save').click();
  await expect(page).toHaveURL(/\/products\/\d+$/);
  return page.url().split('/').pop()!;
}

test('oversell is blocked, a valid sale confirms and reduces stock', async ({ page }) => {
  const sku = `E2E-${unique()}`;
  await login(page);
  const productId = await createProduct(page, sku);
  await expect(page.getByTestId('product-qty')).toContainText('3');

  // Draft sale for 5 units while only 3 are in stock.
  // مسودة بيع بـ 5 وحدات والمتوفر 3 فقط.
  await page.goto('/sales/new');
  await page.locator('#partner').selectOption({ index: 1 });
  await page.getByTestId('line-product').first().selectOption(productId);
  await page.getByTestId('line-qty').first().fill('5');
  await page.getByTestId('save-order').click();
  await expect(page).toHaveURL(/\/sales\/\d+$/);
  await expect(page.getByTestId('order-status')).toBeVisible();

  await page.getByTestId('complete-order').click();
  await page.getByTestId('confirm-yes').click();
  // The server refuses with INSUFFICIENT_STOCK and the message names the SKU.
  // الخادم يرفض بخطأ INSUFFICIENT_STOCK والرسالة فيها رمز المنتج.
  await expect(page.getByTestId('order-error')).toContainText(sku);

  // Reduce to 3, save, confirm again.
  // نخفّضها إلى 3، نحفظ، ونأكد مرة ثانية.
  await page.getByTestId('line-qty').first().fill('3');
  await page.getByTestId('save-order').click();
  await expect(page.getByTestId('save-order')).toBeEnabled();
  // 3 × 15.00 = 45.00, VAT 6.75, total 51.75.
  // 3 × 15.00 = 45.00، الضريبة 6.75، الإجمالي 51.75.
  await expect(page.getByTestId('order-total')).toContainText('51.75');
  await page.getByTestId('complete-order').click();
  await page.getByTestId('confirm-yes').click();
  await expect(page.getByTestId('order-status')).toHaveClass(/badge--CONFIRMED/);
  await expect(page.getByTestId('order-total')).toContainText('51.75');

  await page.goto(`/products/${productId}`);
  await expect(page.getByTestId('product-qty')).toContainText('0');
});

test('manual adjustment requires a reason and updates the ledger', async ({ page }) => {
  const sku = `E2E-${unique()}`;
  await login(page);
  const productId = await createProduct(page, sku);

  await page.getByTestId('adjust').click();
  await page.locator('#adj-qty').fill('2');
  // No reason yet: the dialog must refuse to save.
  // بدون سبب: النافذة لازم ترفض الحفظ.
  await page.getByTestId('adjust-save').click();
  await expect(page.locator('#adj-note-err')).not.toBeEmpty();

  await page.locator('#adj-note').fill('Monthly count');
  await page.getByTestId('adjust-save').click();
  await expect(page.getByTestId('product-qty')).toContainText('5');

  // The movements page filtered by this product shows the opening stock and the adjustment.
  // صفحة الحركات مفلترة بهذا المنتج تعرض المخزون الافتتاحي والتسوية.
  await page.goto(`/movements?productId=${productId}`);
  const rows = page.getByTestId('movements-table').locator('tbody tr');
  await expect(rows).toHaveCount(2);
  await expect(rows.first()).toContainText('Monthly count');
});

test('server validation errors appear next to the field (duplicate SKU)', async ({ page }) => {
  const sku = `E2E-${unique()}`;
  await login(page);
  await createProduct(page, sku);
  await page.goto('/products/new');
  await page.locator('#sku').fill(sku);
  await page.locator('#name').fill('Duplicate');
  await page.locator('#categoryId').selectOption({ index: 1 });
  await page.locator('#unit').fill('pcs');
  await page.locator('#costPrice').fill('1');
  await page.locator('#salePrice').fill('2');
  await page.getByTestId('save').click();
  await expect(page.getByTestId('form-error')).toBeVisible();
  await expect(page).toHaveURL(/\/products\/new$/);
});
