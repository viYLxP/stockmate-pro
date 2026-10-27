import { ChangeDetectionStrategy, Component, computed, inject, input, output } from '@angular/core';
import { RouterLink } from '@angular/router';

import { Fulfilment, PaymentMethod, ShopOrder } from '../core/api/models';
import { FormatService } from '../core/i18n/format.service';
import { I18nService } from '../core/i18n/i18n.service';
import { TranslatePipe } from '../core/i18n/translate.pipe';
import { localPhone, localized, tileHue, withVat } from '../core/shop';
import { IconComponent } from '../core/ui/icon.component';
import { PaymentBadgeComponent } from './payment-badge.component';
import { PaymentPanelComponent } from './payment-panel.component';

const STEPS: Fulfilment[] = ['NEW', 'SHIPPED', 'DELIVERED'];

/**
 * A shop order as its customer sees it: progress, payment, items and the delivery address.
 * Used by the guest tracking page and the account page; the parent sends pay and cancel to its own endpoint.
 * طلب المتجر مثل ما يشوفه صاحبه: المراحل، والدفع، والأصناف، وعنوان التوصيل.
 * تستخدمه صفحة التتبع للزائر وصفحة الحساب؛ والأب يرسل الدفع والإلغاء لرابطه الخاص.
 */
@Component({
  selector: 'app-order-view',
  imports: [RouterLink, TranslatePipe, IconComponent, PaymentBadgeComponent, PaymentPanelComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @let o = order();
    <article class="order-view" data-testid="order-view">
      <header class="order-view__head">
        <div>
          <h2 data-testid="order-number">{{ 'order.title' | t: { number: o.number } }}</h2>
          <p class="muted">{{ 'order.placedOn' | t: { date: fmt.dateTime(o.createdAt) } }}</p>
        </div>
        <div class="order-view__badges">
          @if (cancelled()) {
            <span class="badge badge--danger" data-testid="order-status">{{
              'orderStatus.CANCELLED' | t
            }}</span>
          } @else if (o.fulfilment) {
            <span
              class="badge"
              [class.badge--success]="o.fulfilment === 'DELIVERED'"
              [class.badge--accent]="o.fulfilment !== 'DELIVERED'"
              data-testid="order-status"
              >{{ 'fulfilment.' + o.fulfilment | t }}</span
            >
          }
          <app-payment-badge [status]="o.payment.status" />
        </div>
      </header>

      @if (cancelled()) {
        <div class="alert alert--danger" data-testid="order-cancelled">
          {{ 'order.cancelledOn' | t: { date: fmt.dateTime(o.cancelledAt) } }}
          {{ reasonText() }}
        </div>
      } @else {
        <section class="card" aria-labelledby="progress-title">
          <h3 id="progress-title" class="visually-hidden">{{ 'order.progress' | t }}</h3>
          <ol class="stepper">
            @for (step of steps; track step; let i = $index) {
              <li
                [class.is-done]="i <= stepIndex()"
                [attr.aria-current]="i === stepIndex() ? 'step' : null"
              >
                <span class="stepper__dot">
                  @if (i <= stepIndex()) {
                    <app-icon name="check" />
                  }
                </span>
                <span>{{ 'fulfilment.' + step | t }}</span>
                @if (stepDate(step); as when) {
                  <small class="muted">{{ when }}</small>
                }
              </li>
            }
          </ol>
        </section>
        @if (o.payBy && o.payment.status === 'UNPAID') {
          <p class="alert alert--warning" data-testid="order-pay-by">
            {{ 'order.payBy' | t: { time: fmt.dateTime(o.payBy) } }}
          </p>
        } @else if (o.paymentChoice === 'CASH' && o.payment.status === 'UNPAID') {
          <p class="alert alert--info" data-testid="order-cod">
            {{ 'order.cod' | t: { amount: fmt.money(o.total) } }}
          </p>
        }
      }

      @if (!cancelled() || o.payment.status !== 'UNPAID') {
        <app-payment-panel [payment]="o.payment" [busy]="busy()" (pay)="pay.emit($event)" />
      }

      <section class="card" aria-labelledby="items-title">
        <div class="card__header">
          <h3 id="items-title">{{ 'order.items' | t }}</h3>
        </div>
        <ul class="cart-lines">
          @for (l of o.lines; track l.productId) {
            <li class="cart-line">
              <span class="product-tile" [style.--hue]="hue(l.productId)"
                ><app-icon name="package"
              /></span>
              <div>
                <a class="cart-line__name" [routerLink]="['/shop/products', l.productId]">{{
                  name(l.name, l.nameAr)
                }}</a>
                <span class="list__sub"
                  >{{ fmt.money(vat(l.unitPrice)) }}
                  {{ 'order.qty' | t: { qty: l.quantity } }}</span
                >
              </div>
              <strong class="num">{{ fmt.money(vat(l.lineTotal)) }}</strong>
            </li>
          }
        </ul>
        <dl class="totals">
          <dt>{{ 'cart.subtotal' | t }}</dt>
          <dd class="num">{{ fmt.money(o.subtotal) }}</dd>
          <dt>{{ 'cart.vat' | t }}</dt>
          <dd class="num">{{ fmt.money(o.vat) }}</dd>
          <dt class="grand">{{ 'cart.total' | t }}</dt>
          <dd class="grand num" data-testid="order-total">{{ fmt.money(o.total) }}</dd>
        </dl>
      </section>

      <section class="card" aria-labelledby="ship-title">
        <h3 id="ship-title">{{ 'order.deliverTo' | t }}</h3>
        <p>
          <strong dir="auto">{{ o.shipTo.name }}</strong> ·
          <span dir="ltr">{{ phone(o.shipTo.phone) }}</span>
        </p>
        <p class="muted" dir="auto">
          {{ 'city.' + o.shipTo.city | t }} · {{ o.shipTo.district }} · {{ o.shipTo.address }}
        </p>
      </section>

      @if (o.cancellable) {
        <div>
          <button
            type="button"
            class="btn btn--danger"
            (click)="cancelOrder.emit()"
            [disabled]="busy()"
            data-testid="order-cancel"
          >
            <app-icon name="x" /> {{ 'order.cancel' | t }}
          </button>
        </div>
      }
    </article>
  `,
})
export class OrderViewComponent {
  protected readonly fmt = inject(FormatService);
  private readonly i18n = inject(I18nService);

  readonly order = input.required<ShopOrder>();
  readonly busy = input(false);
  readonly pay = output<PaymentMethod>();
  readonly cancelOrder = output<void>();

  protected readonly steps = STEPS;
  protected readonly cancelled = computed(() => this.order().status === 'CANCELLED');
  protected readonly stepIndex = computed(() => STEPS.indexOf(this.order().fulfilment ?? 'NEW'));

  // The system's own reasons are translated; a reason typed by staff is shown as written.
  // أسباب النظام تتترجم؛ والسبب اللي كتبه الموظف ينعرض مثل ما هو.
  protected readonly reasonText = computed(() => {
    const reason = this.order().cancelReason;
    if (!reason) return '';
    if (reason === 'CUSTOMER' || reason === 'PAYMENT_TIMEOUT') {
      return this.i18n.t(`order.reason${reason}`);
    }
    return this.i18n.t('order.reasonOther', { reason });
  });

  protected stepDate(step: Fulfilment): string | null {
    const o = this.order();
    const at = step === 'NEW' ? o.createdAt : step === 'SHIPPED' ? o.shippedAt : o.deliveredAt;
    return at ? this.fmt.date(at) : null;
  }

  // Lines include VAT like the shop; the totals are the server's. / الأسطر شاملة الضريبة؛ والمجاميع من الخادم.
  protected vat(amount: number): number {
    return withVat(amount);
  }

  protected name(en: string, ar: string | null): string {
    return localized(this.i18n.lang(), en, ar);
  }

  protected phone(value: string): string {
    return localPhone(value.replace(/\D/g, ''));
  }

  // Order lines carry no category, so the tile colour follows the product id. / الأسطر ما فيها تصنيف، فاللون يتبع رقم المنتج.
  protected hue(productId: number): number {
    return tileHue(productId);
  }
}
