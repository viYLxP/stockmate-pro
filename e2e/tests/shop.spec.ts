import { APIRequestContext, Page, expect, test } from '@playwright/test';

import { ADMIN, CUSTOMER, expectNoHorizontalScroll, login, unique, uniquePhone } from './helpers';

/**
 * The online shop end to end, on the real stack: visitor checkout (cash and card), tracking, cancelling with
 * the stock coming back, customer sign-up with Tabby instalments, and staff shipping and delivering.
 * المتجر الإلكتروني من أوله لآخره على النظام الحقيقي: طلب الزائر (كاش وبطاقة)، والتتبع، والإلغاء ورجوع
 * المخزون، وتسجيل عميل جديد والدفع بتقسيط تابي، والموظف يشحن ويوصّل.
 */

interface ShopProduct {
  id: number;
  sku: string;
  price: number;
  inStock: boolean;
  left: number | null;
}

// A shop product with plenty of stock (the shop hides the exact count above 5).
// منتج في المتجر مخزونه كثير (المتجر يخفي العدد بالضبط فوق 5).
async function stockedProduct(request: APIRequestContext): Promise<ShopProduct> {
  const res = await request.get('/api/shop/products?size=48&sort=PRICE_DESC');
  expect(res.ok()).toBeTruthy();
  const products = (await res.json()).content as ShopProduct[];
  const found = products.find((p) => p.inStock && p.left === null);
  expect(found, 'a stocked shop product').toBeTruthy();
  return found!;
}

async function adminToken(request: APIRequestContext): Promise<string> {
  const res = await request.post('/api/auth/login', { data: ADMIN });
  return (await res.json()).token as string;
}

async function stockOf(request: APIRequestContext, productId: number): Promise<number> {
  const res = await request.get(`/api/products/${productId}`, {
    headers: { Authorization: `Bearer ${await adminToken(request)}` },
  });
  return (await res.json()).quantity as number;
}

async function addToCart(page: Page, product: ShopProduct, quantity = 1): Promise<void> {
  await page.goto(`/shop/products/${product.id}`);
  await expect(page.getByTestId('product-detail')).toBeVisible();
  if (quantity > 1) {
    await page.getByTestId('qty-input').fill(String(quantity));
    await page.getByTestId('qty-input').blur();
  }
  await page.getByTestId('add-to-cart').click();
  // The cart is saved in the browser; wait until the header shows it. / السلة تنحفظ في المتصفح؛ ننتظر لين يبان العدد.
  await expect(page.getByTestId('cart-count')).toBeVisible();
}

async function fillGuestCheckout(page: Page, phone: string, method: string): Promise<void> {
  await page.goto('/checkout');
  await page.getByTestId('co-name').fill('E2E Guest');
  await page.getByTestId('co-phone').fill(phone);
  await page.getByTestId('co-district').fill('Al Olaya');
  await page.getByTestId('co-address').fill('King Fahd Road, building 12');
  await page.getByTestId(`co-method-${method}`).check({ force: true });
  await expect(page.getByTestId('checkout-total')).toBeVisible();
}

// Opens the order in the office: search the sales list by its number. / يفتح الطلب في المكتب بالبحث برقمه.
async function openInOffice(page: Page, number: string): Promise<void> {
  await page.goto('/sales?channel=ONLINE');
  await page.locator('#order-search').fill(number);
  await page.getByRole('link', { name: number }).click();
  await expect(page.getByTestId('order-channel')).toBeVisible();
}

test.describe('online shop', () => {
  test('a visitor orders with cash on delivery, tracks it and cancels; the stock comes back', async ({
    page,
    request,
  }) => {
    const product = await stockedProduct(request);
    const before = await stockOf(request, product.id);
    const phone = uniquePhone();

    await page.goto('/shop');
    await expect(page.locator('html')).toHaveAttribute('dir', 'rtl');
    await expect(page.getByTestId('product-grid')).toBeVisible();

    await addToCart(page, product, 2);
    await expect(page.getByTestId('cart-count')).toHaveText('2');
    await page.getByTestId('nav-cart').click();
    await expect(page.getByTestId('cart-total')).toBeVisible();
    await page.getByTestId('go-checkout').click();
    await expect(page).toHaveURL(/\/checkout$/);

    // Submitting empty shows the field errors and sends nothing. / الإرسال فاضي يعرض الأخطاء وما يرسل شي.
    await page.getByTestId('place-order').click();
    await expect(page.locator('#co-name-err')).not.toBeEmpty();
    await expect(page.locator('#co-phone-err')).not.toBeEmpty();

    await fillGuestCheckout(page, phone, 'CASH');
    await page.getByTestId('place-order').click();
    await expect(page.getByTestId('order-placed')).toBeVisible();
    const number = (await page.getByTestId('placed-number').textContent())!.trim();
    expect(number).toMatch(/^SO-\d{6}$/);
    await expect(page.getByTestId('cart-count')).toHaveCount(0);
    // Stock is taken when the order is placed. / المخزون ينخصم وقت الطلب.
    expect(await stockOf(request, product.id)).toBe(before - 2);

    // Tracking needs the right phone too. / التتبع يحتاج الجوال الصحيح برضو.
    await page.goto(`/track?number=${number}`);
    await page.getByTestId('track-phone').fill('0500000001');
    await page.getByTestId('track-submit').click();
    await expect(page.getByTestId('track-error')).toBeVisible();
    await page.getByTestId('track-phone').fill(phone);
    await page.getByTestId('track-submit').click();
    await expect(page.getByTestId('order-number')).toContainText(number);
    await expect(page.getByTestId('order-cod')).toBeVisible();

    await page.getByTestId('order-cancel').click();
    await page.getByTestId('confirm-yes').click();
    await expect(page.getByTestId('order-cancelled')).toBeVisible();
    expect(await stockOf(request, product.id)).toBe(before);
  });

  test('a visitor pays by card: a declined card, then an approved one', async ({ page, request }) => {
    const product = await stockedProduct(request);
    const phone = uniquePhone();
    await addToCart(page, product);
    await fillGuestCheckout(page, phone, 'CARD');
    await page.getByTestId('place-order').click();

    await expect(page).toHaveURL(/\/pay\/[0-9a-f-]{36}$/);
    await expect(page.getByTestId('sandbox-banner')).toBeVisible();
    await page.getByTestId('use-card-0002').click();
    await page.getByTestId('card-submit').click();
    await expect(page.getByTestId('pay-failed')).toBeVisible();

    await page.getByTestId('use-card-1111').click();
    await page.getByTestId('card-submit').click();
    await expect(page.getByTestId('pay-success')).toBeVisible();

    // "Back to my order" opens tracking with the number filled in. / "الرجوع لطلبي" يفتح التتبع ورقم الطلب معبّى.
    await page.getByRole('link', { name: /طلبي|my order/i }).click();
    await page.getByTestId('track-phone').fill(phone);
    await page.getByTestId('track-submit').click();
    await expect(page.locator('[data-payment="PAID"]').first()).toBeVisible();
    await expect(page.getByTestId('payment-paid')).toContainText('1111');
  });

  test('instalments are offered only within their limits', async ({ page, request }) => {
    const cheap = (await (await request.get('/api/shop/products?size=48&sort=PRICE_ASC')).json())
      .content.find((p: ShopProduct) => p.inStock && p.price < 100) as ShopProduct;
    await addToCart(page, cheap);
    await page.goto('/checkout');
    await expect(page.getByTestId('checkout-total')).toBeVisible();
    await expect(page.getByTestId('co-method-TABBY')).toBeDisabled();
    await expect(page.getByTestId('co-method-TAMARA')).toBeDisabled();
    await expect(page.getByTestId('co-method-CARD')).toBeEnabled();
  });

  test('a new customer signs up, pays with Tabby; staff ship and deliver; the customer sees it', async ({
    page,
    browser,
    request,
  }) => {
    const product = await stockedProduct(request);
    // Instalments start at 100 SAR. / التقسيط يبدأ من 100 ريال.
    const quantity = Math.ceil(100 / product.price);
    const phone = uniquePhone();
    const name = `E2E Customer ${unique()}`;

    await page.goto('/register');
    await page.getByTestId('register-name').fill(name);
    await page.getByTestId('register-phone').fill(phone);
    await page.getByTestId('register-password').fill('Secret@123');
    await page.getByTestId('register-confirm').fill('Secret@124');
    await page.getByTestId('register-submit').click();
    await expect(page.locator('#r-confirm-err')).not.toBeEmpty();
    await page.getByTestId('register-confirm').fill('Secret@123');
    await page.getByTestId('register-submit').click();
    await expect(page).toHaveURL(/\/account$/);
    await expect(page.getByTestId('account-empty')).toBeVisible();

    await addToCart(page, product, quantity);
    await page.goto('/checkout');
    // A customer is not asked for name and phone again. / العميل ما ينسأل عن اسمه وجواله مرة ثانية.
    await expect(page.getByTestId('checkout-signed-in')).toContainText(name);
    await expect(page.getByTestId('co-name')).toHaveCount(0);
    await page.getByTestId('co-district').fill('Al Rawdah');
    await page.getByTestId('co-address').fill('Prince Sultan Street 5');
    await page.getByTestId('co-method-TABBY').check({ force: true });
    await page.getByTestId('place-order').click();

    await expect(page.getByTestId('bnpl-sheet')).toBeVisible();
    await expect(page.getByTestId('bnpl-plan').locator('li')).toHaveCount(4);
    await page.getByTestId('bnpl-confirm').click();
    await expect(page.getByTestId('pay-success')).toBeVisible();
    await page.getByRole('link', { name: /طلبي|my order/i }).click();
    await expect(page).toHaveURL(/\/account\/orders\/SO-\d{6}$/);
    const number = page.url().split('/').pop()!;
    await expect(page.locator('[data-payment="PAID"]').first()).toBeVisible();

    // Staff in a separate browser session. / الموظف في جلسة متصفح منفصلة.
    const office = await (await browser.newContext({ locale: 'ar-SA' })).newPage();
    await login(office);
    await openInOffice(office, number);
    await expect(office.getByTestId('attempt-PAID')).toBeVisible();
    await office.getByTestId('ship-order').click();
    await office.getByTestId('confirm-yes').click();
    await expect(office.getByTestId('order-fulfilment')).toContainText('الطريق');
    await office.getByTestId('deliver-order').click();
    await office.getByTestId('confirm-yes').click();
    await expect(office.getByTestId('order-fulfilment')).toContainText('توصيل');
    await expect(office.getByTestId('cancel-order')).toHaveCount(0);

    await page.reload();
    await expect(page.getByTestId('order-status')).toContainText('توصيل');
    await expect(page.getByTestId('order-cancel')).toHaveCount(0);
  });

  test('staff deliver a cash order and the cash is recorded as paid', async ({ page, request }) => {
    const product = await stockedProduct(request);
    await addToCart(page, product);
    await fillGuestCheckout(page, uniquePhone(), 'CASH');
    await page.getByTestId('place-order').click();
    const number = (await page.getByTestId('placed-number').textContent())!.trim();

    await login(page);
    await openInOffice(page, number);
    await page.getByTestId('ship-order').click();
    await page.getByTestId('confirm-yes').click();
    await expect(page.getByTestId('deliver-order')).toBeVisible();
    await page.getByTestId('deliver-order').click();
    await page.getByTestId('confirm-yes').click();
    await expect(page.locator('[data-payment="PAID"]').first()).toBeVisible();
    await expect(page.getByTestId('attempt-PAID')).toContainText(/كاش|Cash/);
  });

  test('staff cancel a paid order with a reason; it is refunded and the customer sees why', async ({
    page,
    request,
  }) => {
    const product = await stockedProduct(request);
    const phone = uniquePhone();
    await addToCart(page, product);
    await fillGuestCheckout(page, phone, 'APPLE_PAY');
    await page.getByTestId('place-order').click();
    await page.getByTestId('wallet-confirm').click();
    await expect(page.getByTestId('pay-success')).toBeVisible();
    const number = (await page.getByTestId('pay-amount').locator('xpath=..').textContent())!.match(
      /SO-\d{6}/,
    )![0];

    const office = await (await page.context().browser()!.newContext({ locale: 'ar-SA' })).newPage();
    await login(office);
    await openInOffice(office, number);
    await office.getByTestId('cancel-order').click();
    await office.getByTestId('prompt-input').fill('Out of delivery area');
    await office.getByTestId('prompt-ok').click();
    await expect(office.getByTestId('cancel-reason')).toContainText('Out of delivery area');
    await expect(office.getByTestId('attempt-REFUNDED')).toBeVisible();

    await page.goto(`/track?number=${number}`);
    await page.getByTestId('track-phone').fill(phone);
    await page.getByTestId('track-submit').click();
    await expect(page.getByTestId('order-cancelled')).toContainText('Out of delivery area');
    await expect(page.locator('[data-payment="REFUNDED"]').first()).toBeVisible();
  });

  test('the account asks visitors to sign in and brings them back', async ({ page }) => {
    await page.goto('/account');
    await expect(page).toHaveURL(/\/login\?returnUrl=%2Faccount/);
    await page.locator('#username').fill(CUSTOMER.username);
    await page.locator('#password').fill(CUSTOMER.password);
    await page.locator('form button[type=submit]').click();
    await expect(page).toHaveURL(/\/account$/);
    await expect(page.getByTestId('account-orders')).toBeVisible();
    // Customers never reach the office. / العميل ما يوصل للمكتب أبدًا.
    await page.goto('/dashboard');
    await expect(page).toHaveURL(/\/account$/);
  });

  for (const lang of ['ar', 'en'] as const) {
    test(`shop pages render without horizontal scroll or console errors (${lang})`, async ({
      page,
      request,
    }) => {
      const product = await stockedProduct(request);
      const errors: string[] = [];
      page.on('console', (msg) => {
        if (msg.type() === 'error') errors.push(msg.text());
      });
      page.on('pageerror', (err) => errors.push(err.message));
      await page.addInitScript((l) => localStorage.setItem('stockmate.lang', l), lang);

      await addToCart(page, product);
      for (const path of ['/shop', `/shop/products/${product.id}`, '/cart', '/checkout', '/track', '/register', '/login']) {
        await page.goto(path);
        await expect(page.locator('h1').first()).toBeVisible();
        await expect(page.locator('.loading')).toHaveCount(0);
        await expectNoHorizontalScroll(page);
      }
      expect(errors).toEqual([]);
    });
  }
});
