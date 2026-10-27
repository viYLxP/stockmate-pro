import { Injectable, computed, effect, signal } from '@angular/core';

import { CartItem, ShopProduct } from './api/models';

const STORAGE_KEY = 'stockmate.cart';
// Same limits as the server's checkout. / نفس حدود الخادم في إتمام الطلب.
export const MAX_QUANTITY = 99;
export const MAX_LINES = 30;

/**
 * One cart line. The name and price are a snapshot for showing the cart quickly; the cart page always asks the
 * server for today's prices and stock (POST /shop/quote) before checkout.
 * سطر في السلة. الاسم والسعر نسخة لعرض السلة بسرعة؛ وصفحة السلة دايم تسأل الخادم عن أسعار ومخزون اليوم قبل الدفع.
 */
export interface CartLine {
  productId: number;
  quantity: number;
  sku: string;
  name: string;
  nameAr: string | null;
  categoryId: number;
  price: number;
}

/**
 * The shopping cart, kept in the browser (localStorage) so it survives a reload and works without an account.
 * سلة المشتريات، محفوظة في المتصفح (localStorage) حتى تبقى بعد تحديث الصفحة وتشتغل بدون حساب.
 */
@Injectable({ providedIn: 'root' })
export class CartService {
  private readonly linesSignal = signal<CartLine[]>(readCart());

  readonly lines = this.linesSignal.asReadonly();
  readonly count = computed(() => this.linesSignal().reduce((sum, l) => sum + l.quantity, 0));
  readonly isEmpty = computed(() => this.linesSignal().length === 0);
  // What the server needs: ids and quantities only. / اللي يحتاجه الخادم: الأرقام والكميات بس.
  readonly items = computed<CartItem[]>(() =>
    this.linesSignal().map((l) => ({ productId: l.productId, quantity: l.quantity })),
  );

  constructor() {
    effect(() => {
      const lines = this.linesSignal();
      try {
        localStorage.setItem(STORAGE_KEY, JSON.stringify(lines));
      } catch {
        // Private mode or blocked storage: the cart works until the page is closed.
        // وضع خاص أو تخزين محجوب: السلة تشتغل لين تنقفل الصفحة.
      }
    });
  }

  /**
   * Adds to the line of this product (or a new line). Returns false when the cart is full.
   * يضيف لسطر هالمنتج (أو سطر جديد). يرجّع false لو السلة مليانة.
   */
  add(product: ShopProduct, quantity = 1): boolean {
    const lines = this.linesSignal();
    const existing = lines.find((l) => l.productId === product.id);
    if (!existing && lines.length >= MAX_LINES) return false;
    const snapshot = {
      sku: product.sku,
      name: product.name,
      nameAr: product.nameAr,
      categoryId: product.categoryId,
      price: product.price,
    };
    this.linesSignal.set(
      existing
        ? lines.map((l) =>
            l.productId === product.id
              ? { ...l, ...snapshot, quantity: clamp(l.quantity + quantity) }
              : l,
          )
        : [...lines, { productId: product.id, quantity: clamp(quantity), ...snapshot }],
    );
    return true;
  }

  setQuantity(productId: number, quantity: number): void {
    this.linesSignal.update((lines) =>
      lines.map((l) => (l.productId === productId ? { ...l, quantity: clamp(quantity) } : l)),
    );
  }

  remove(productId: number): void {
    this.linesSignal.update((lines) => lines.filter((l) => l.productId !== productId));
  }

  clear(): void {
    this.linesSignal.set([]);
  }
}

function clamp(quantity: number): number {
  return Math.min(MAX_QUANTITY, Math.max(1, Math.floor(quantity) || 1));
}

// A damaged or old saved cart is dropped rather than breaking the shop. / السلة المحفوظة التالفة تنشال بدل ما تخرب المتجر.
function readCart(): CartLine[] {
  try {
    const parsed: unknown = JSON.parse(localStorage.getItem(STORAGE_KEY) ?? '[]');
    if (!Array.isArray(parsed)) return [];
    return parsed
      .filter(
        (l): l is CartLine =>
          typeof l === 'object' &&
          l !== null &&
          typeof l.productId === 'number' &&
          typeof l.quantity === 'number' &&
          typeof l.name === 'string' &&
          typeof l.price === 'number',
      )
      .slice(0, MAX_LINES)
      .map((l) => ({ ...l, quantity: clamp(l.quantity) }));
  } catch {
    return [];
  }
}
