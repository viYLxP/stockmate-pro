import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { Router, RouterLink } from '@angular/router';

import { ApiError, errorMessage, toApiError } from '../../core/api/api-error';
import { ApiService } from '../../core/api/api.service';
import { CheckoutResponse, PaymentMethod, ShopOrder } from '../../core/api/models';
import { openCheckout } from '../../core/checkout';
import { FormatService } from '../../core/i18n/format.service';
import { I18nService } from '../../core/i18n/i18n.service';
import { TranslatePipe } from '../../core/i18n/translate.pipe';
import { ConfirmService } from '../../core/ui/confirm.service';
import { IconComponent } from '../../core/ui/icon.component';
import { ToastService } from '../../core/ui/toast.service';
import { OrderViewComponent } from '../../shared/order-view.component';

/**
 * One order in the customer's account (/account/orders/:number): progress, pay and cancel.
 * طلب واحد في حساب العميل: المراحل والدفع والإلغاء.
 */
@Component({
  selector: 'app-account-order',
  imports: [RouterLink, TranslatePipe, IconComponent, OrderViewComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="page-narrow">
      <a class="breadcrumb" routerLink="/account">
        <app-icon name="chevron" class="icon--back" [flip]="true" /> {{ 'account.back' | t }}
      </a>
      @if (order.error()) {
        <div class="empty-state card section-gap" data-testid="account-order-missing">
          <p>{{ loadErrorText() }}</p>
          <a class="btn" routerLink="/account">{{ 'account.back' | t }}</a>
        </div>
      } @else if (order.value(); as o) {
        @if (errorText(); as message) {
          <div class="alert alert--danger section-gap" role="alert" data-testid="order-error">
            {{ message }}
          </div>
        }
        <div class="section-gap">
          <app-order-view
            [order]="o"
            [busy]="busy()"
            (pay)="pay($event)"
            (cancelOrder)="cancel()"
          />
        </div>
      } @else {
        <div class="loading" role="status">
          <span class="spinner"></span>{{ 'app.loading' | t }}
        </div>
      }
    </div>
  `,
})
export class AccountOrderPage {
  private readonly api = inject(ApiService);
  private readonly router = inject(Router);
  private readonly toast = inject(ToastService);
  private readonly confirm = inject(ConfirmService);
  private readonly fmt = inject(FormatService);
  private readonly i18n = inject(I18nService);

  readonly number = input.required<string>();

  protected readonly order = rxResource({
    params: () => this.number(),
    stream: ({ params }) => this.api.get<ShopOrder>(`/account/orders/${params}`),
  });
  protected readonly loadErrorText = computed(() => {
    const err = toApiError(this.order.error());
    return err.code === 'NOT_FOUND' ? this.i18n.t('track.notFound') : errorMessage(this.i18n, err);
  });
  protected readonly busy = signal(false);
  private readonly apiError = signal<ApiError | null>(null);
  protected readonly errorText = computed(() => {
    const err = this.apiError();
    return err ? errorMessage(this.i18n, err) : null;
  });

  protected pay(method: PaymentMethod): void {
    this.busy.set(true);
    this.apiError.set(null);
    this.api.post<CheckoutResponse>(`/account/orders/${this.number()}/pay`, { method }).subscribe({
      next: (checkout) => openCheckout(this.router, checkout),
      error: (err) => {
        this.busy.set(false);
        this.apiError.set(toApiError(err));
        this.order.reload();
      },
    });
  }

  protected async cancel(): Promise<void> {
    const o = this.order.value();
    if (!o) return;
    const ok = await this.confirm.ask({
      title: this.i18n.t('common.confirmTitle'),
      message:
        o.payment.status === 'PAID'
          ? this.i18n.t('order.cancelPaidConfirm', {
              number: o.number,
              amount: this.fmt.money(o.total),
            })
          : this.i18n.t('order.cancelConfirm', { number: o.number }),
      confirmLabel: this.i18n.t('order.cancel'),
      danger: true,
    });
    if (!ok) return;
    this.busy.set(true);
    this.apiError.set(null);
    this.api.post<ShopOrder>(`/account/orders/${o.number}/cancel`).subscribe({
      next: (updated) => {
        this.busy.set(false);
        this.order.set(updated);
        this.toast.success(this.i18n.t('order.cancelled'));
      },
      error: (err) => {
        this.busy.set(false);
        this.apiError.set(toApiError(err));
      },
    });
  }
}
