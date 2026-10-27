import { TestBed } from '@angular/core/testing';

import { ShopProduct } from './api/models';
import { CartService, MAX_QUANTITY } from './cart.service';

const product = (id: number, price = 10): ShopProduct => ({
  id,
  sku: `SKU-${id}`,
  name: `Product ${id}`,
  nameAr: null,
  description: null,
  descriptionAr: null,
  categoryId: 1,
  categoryName: 'Food',
  categoryNameAr: null,
  unit: 'piece',
  price,
  inStock: true,
  left: null,
});

describe('CartService', () => {
  beforeEach(() => localStorage.clear());

  it('adds to the same line, caps the quantity and counts items', () => {
    const cart = TestBed.inject(CartService);
    cart.add(product(1), 2);
    cart.add(product(1), 3);
    cart.add(product(2));
    expect(cart.lines().length).toBe(2);
    expect(cart.count()).toBe(6);
    cart.setQuantity(1, 500);
    expect(cart.lines()[0].quantity).toBe(MAX_QUANTITY);
    cart.setQuantity(1, 0);
    expect(cart.lines()[0].quantity).toBe(1);
    expect(cart.items()).toEqual([
      { productId: 1, quantity: 1 },
      { productId: 2, quantity: 1 },
    ]);
  });

  it('keeps the cart in localStorage and drops a damaged saved cart', () => {
    const cart = TestBed.inject(CartService);
    cart.add(product(7), 2);
    TestBed.tick();
    expect(JSON.parse(localStorage.getItem('stockmate.cart') ?? '[]')[0].quantity).toBe(2);

    TestBed.resetTestingModule();
    localStorage.setItem('stockmate.cart', '{"not":"a list"}');
    expect(TestBed.inject(CartService).isEmpty()).toBe(true);
  });

  it('removes lines and clears', () => {
    const cart = TestBed.inject(CartService);
    cart.add(product(1));
    cart.add(product(2));
    cart.remove(1);
    expect(cart.lines().map((l) => l.productId)).toEqual([2]);
    cart.clear();
    expect(cart.count()).toBe(0);
  });
});
