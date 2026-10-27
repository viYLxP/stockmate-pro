import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { ShopOrder } from '../core/api/models';
import { I18nService } from '../core/i18n/i18n.service';
import { OrderViewComponent } from './order-view.component';

const order: ShopOrder = {
  number: 'SO-000042',
  status: 'CONFIRMED',
  fulfilment: 'NEW',
  createdAt: '2026-10-27T09:00:00Z',
  payBy: '2026-10-27T09:30:00Z',
  shippedAt: null,
  deliveredAt: null,
  cancelledAt: null,
  cancelReason: null,
  lines: [
    {
      productId: 3,
      sku: 'RIC-001',
      name: 'Basmati rice 5kg',
      nameAr: 'أرز بسمتي 5 كجم',
      quantity: 2,
      unitPrice: 50,
      lineTotal: 100,
    },
  ],
  subtotal: 100,
  vat: 15,
  total: 115,
  paymentChoice: 'CARD',
  payment: {
    status: 'UNPAID',
    payable: true,
    amountDue: 115,
    methods: ['CARD', 'APPLE_PAY'],
    instalments: [],
    method: null,
    cardBrand: null,
    cardLast4: null,
    paidAt: null,
  },
  shipTo: {
    name: 'Nora',
    phone: '+966 55 300 1010',
    email: null,
    city: 'RIYADH',
    district: 'Al Malqa',
    address: 'King Fahd Rd',
  },
  cancellable: true,
};

function render(o: ShopOrder, lang: 'en' | 'ar' = 'en') {
  TestBed.configureTestingModule({ providers: [provideRouter([])] });
  TestBed.inject(I18nService).setLang(lang);
  const fixture = TestBed.createComponent(OrderViewComponent);
  fixture.componentRef.setInput('order', o);
  let cancelled = 0;
  fixture.componentInstance.cancelOrder.subscribe(() => cancelled++);
  fixture.detectChanges();
  const el = fixture.nativeElement as HTMLElement;
  const q = (id: string) => el.querySelector<HTMLElement>(`[data-testid="${id}"]`);
  return { el, q, cancelled: () => cancelled };
}

describe('OrderViewComponent', () => {
  it('shows the pay-by deadline, the payment panel and a cancel button for an unpaid order', () => {
    const { q, cancelled } = render(order);
    expect(q('order-status')!.textContent).toContain('Being prepared');
    expect(q('order-pay-by')).not.toBeNull();
    expect(q('pay-now')).not.toBeNull();
    q('order-cancel')!.click();
    expect(cancelled()).toBe(1);
  });

  it('shows Arabic product names and the local phone format in the Arabic shop', () => {
    const { el } = render(order, 'ar');
    expect(el.textContent).toContain('أرز بسمتي 5 كجم');
    expect(el.textContent).toContain('0553001010');
  });

  it('explains a cancellation by timeout and hides payment and cancel', () => {
    const { q } = render({
      ...order,
      status: 'CANCELLED',
      cancelledAt: '2026-10-27T09:31:00Z',
      cancelReason: 'PAYMENT_TIMEOUT',
      cancellable: false,
      payment: { ...order.payment, payable: false, methods: [] },
    });
    expect(q('order-cancelled')!.textContent).toContain('not paid in time');
    expect(q('payment-panel')).toBeNull();
    expect(q('order-cancel')).toBeNull();
  });

  it('tells a cash-on-delivery customer what to pay the driver', () => {
    const { q } = render({
      ...order,
      payBy: null,
      paymentChoice: 'CASH',
      payment: { ...order.payment, payable: false, methods: [] },
    });
    expect(q('order-cod')!.textContent).toContain('115.00');
  });
});
