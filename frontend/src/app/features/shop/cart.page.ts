import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';

import { errorMessage, toApiError } from '../../core/api/api-error';
import { ApiService } from '../../core/api/api.service';
import { Quote, QuoteLine } from '../../core/api/models';
import { CartLine, CartService } from '../../core/cart.service';
import { FormatService } from '../../core/i18n/format.service';
import { I18nService } from '../../core/i18n/i18n.service';
import { TranslatePipe } from '../../core/i18n/translate.pipe';
import { localized, tileHue, withVat } from '../../core/shop';
import { IconComponent } from '../../core/ui/icon.component';
import { QuantityComponent } from './quantity.component';

/**
 * The cart. Every change asks the server for today's prices and stock (POST /shop/quote), so the totals shown
 * are the ones the customer will pay, and a sold-out or withdrawn product is flagged before checkout.
 * السلة. كل تغيير يسأل الخادم عن أسعار ومخزون اليوم، فالمجموع المعروض هو اللي بيدفعه العميل،
 * والمنتج اللي خلص أو انسحب ينبّه عليه قبل الدفع.
 */
@Component({
  selector: 'app-cart',
  imports: [RouterLink, TranslatePipe, IconComponent, QuantityComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="shop-page">
      <header class="page-header section-gap">
        <h1>{{ 'cart.title' | t }}</h1>
      </header>
      @if (cart.isEmpty()) {
        <div class="empty-state card" data-testid="cart-empty">
          <p>{{ 'cart.empty' | t }}</p>
          <a class="btn btn--primary" routerLink="/shop">{{ 'cart.browse' | t }}</a>
        </div>
      } @else {
        <div class="cart-layout">
          <section class="card" [attr.aria-label]="'cart.title' | t">
            <ul class="cart-lines" data-testid="cart-lines">
              @for (l of cart.lines(); track l.productId) {
                @let q = quoteLine(l.productId);
                <li class="cart-line" [attr.data-testid]="'cart-line-' + l.sku">
                  <span class="product-tile" [style.--hue]="hue(l.categoryId)"
                    ><app-icon name="package"
                  /></span>
                  <div>
                    <a class="cart-line__name" [routerLink]="['/shop/products', l.productId]">{{
                      name(l)
                    }}</a>
                    <span class="list__sub">{{
                      fmt.money(q && q.unitPrice !== null ? vat(q.unitPrice) : l.price)
                    }}</span>
                    @if (q?.problem === 'UNAVAILABLE') {
                      <span class="cart-line__problem" data-testid="line-problem">{{
                        'cart.unavailable' | t
                      }}</span>
                    } @else if (q?.problem === 'STOCK') {
                      <span class="cart-line__problem" data-testid="line-problem">{{
                        'cart.stock' | t: { available: fmt.number(q?.available ?? 0) }
                      }}</span>
                    }
                  </div>
                  <div class="cart-line__controls">
                    <app-quantity
                      [value]="l.quantity"
                      (valueChange)="cart.setQuantity(l.productId, $event)"
                    />
                    <strong class="num">{{
                      q && q.lineTotal !== null ? fmt.money(vat(q.lineTotal)) : '—'
                    }}</strong>
                    <button
                      type="button"
                      class="icon-btn icon-btn--danger"
                      (click)="cart.remove(l.productId)"
                      [attr.aria-label]="'cart.remove' | t: { name: name(l) }"
                      data-testid="line-remove"
                    >
                      <app-icon name="trash" />
                    </button>
                  </div>
                </li>
              }
            </ul>
            <p class="section-gap">
              <a routerLink="/shop">{{ 'cart.continue' | t }}</a>
            </p>
          </section>

          <aside class="card cart-summary" [attr.aria-label]="'order.summary' | t">
            <h2>{{ 'order.summary' | t }}</h2>
            @if (quoteError(); as message) {
              <div class="alert alert--danger" role="alert">
                {{ message }}
                <button type="button" class="btn btn--small" (click)="quote.reload()">
                  {{ 'app.retry' | t }}
                </button>
              </div>
            } @else if (quote.value(); as total) {
              <dl class="totals">
                <dt>{{ 'cart.subtotal' | t }}</dt>
                <dd class="num">{{ fmt.money(total.subtotal) }}</dd>
                <dt>{{ 'cart.vat' | t }}</dt>
                <dd class="num">{{ fmt.money(total.vat) }}</dd>
                <dt class="grand">{{ 'cart.total' | t }}</dt>
                <dd class="grand num" data-testid="cart-total">{{ fmt.money(total.total) }}</dd>
              </dl>
            } @else {
              <p class="muted" role="status">{{ 'cart.checking' | t }}</p>
            }
            <a
              class="btn btn--primary btn--block section-gap"
              [class.is-disabled]="!canCheckout()"
              [attr.aria-disabled]="!canCheckout()"
              [routerLink]="canCheckout() ? '/checkout' : null"
              data-testid="go-checkout"
            >
              <app-icon name="lock" /> {{ 'cart.checkout' | t }}
            </a>
            <p class="hint">{{ 'cart.priceNote' | t }}</p>
          </aside>
        </div>
      }
    </div>
  `,
})
export class CartPage {
  private readonly api = inject(ApiService);
  private readonly i18n = inject(I18nService);
  protected readonly fmt = inject(FormatService);
  protected readonly cart = inject(CartService);

  protected readonly quote = rxResource({
    params: () => (this.cart.isEmpty() ? undefined : this.cart.items()),
    stream: ({ params }) => this.api.post<Quote>('/shop/quote', { items: params }),
  });
  protected readonly quoteError = computed(() =>
    this.quote.error() ? errorMessage(this.i18n, toApiError(this.quote.error())) : null,
  );
  protected readonly canCheckout = computed(
    () => !this.quote.isLoading() && !!this.quote.value()?.canCheckout,
  );

  protected quoteLine(productId: number): QuoteLine | undefined {
    return this.quote.value()?.lines.find((l) => l.productId === productId);
  }

  protected name(l: CartLine): string {
    return localized(this.i18n.lang(), l.name, l.nameAr);
  }

  // Lines show VAT-inclusive prices like the shop; the totals come from the server.
  // الأسطر تعرض الأسعار شاملة الضريبة مثل المتجر؛ والمجاميع من الخادم.
  protected vat(amount: number): number {
    return withVat(amount);
  }

  protected hue(categoryId: number): number {
    return tileHue(categoryId);
  }
}
