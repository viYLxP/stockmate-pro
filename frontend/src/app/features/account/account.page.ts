import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';

import { errorMessage, toApiError } from '../../core/api/api-error';
import { ApiService } from '../../core/api/api.service';
import { ShopOrderRow } from '../../core/api/models';
import { AuthService } from '../../core/auth/auth.service';
import { FormatService } from '../../core/i18n/format.service';
import { I18nService } from '../../core/i18n/i18n.service';
import { TranslatePipe } from '../../core/i18n/translate.pipe';
import { IconComponent } from '../../core/ui/icon.component';
import { PaymentBadgeComponent } from '../../shared/payment-badge.component';

/**
 * "My orders": every shop order of the signed-in customer, newest first.
 * "طلباتي": كل طلبات المتجر للعميل المسجّل، الأحدث أول.
 */
@Component({
  selector: 'app-account',
  imports: [RouterLink, TranslatePipe, IconComponent, PaymentBadgeComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="page-narrow">
      <header class="page-header">
        <div>
          <h1>{{ 'account.title' | t }}</h1>
          <p class="muted">{{ 'account.hello' | t: { name: auth.user()?.fullName ?? '' } }}</p>
        </div>
        <div class="page-header__actions">
          <a class="btn" routerLink="/account/profile" data-testid="account-profile"
            ><app-icon name="user" /> {{ 'account.profile' | t }}</a
          >
          <button type="button" class="btn" (click)="auth.logout()" data-testid="account-logout">
            <app-icon name="logout" [flip]="true" /> {{ 'account.logout' | t }}
          </button>
        </div>
      </header>

      @if (errorText(); as message) {
        <div class="alert alert--danger" role="alert">
          {{ message }}
          <button type="button" class="btn btn--small" (click)="orders.reload()">
            {{ 'app.retry' | t }}
          </button>
        </div>
      } @else if (orders.isLoading() && !orders.hasValue()) {
        <div class="loading" role="status">
          <span class="spinner"></span>{{ 'app.loading' | t }}
        </div>
      } @else if (orders.value(); as list) {
        @if (list.length === 0) {
          <div class="empty-state card" data-testid="account-empty">
            <p>{{ 'account.empty' | t }}</p>
            <a class="btn btn--primary" routerLink="/shop">{{ 'account.emptyCta' | t }}</a>
          </div>
        } @else {
          <ul class="account-list" data-testid="account-orders">
            @for (o of list; track o.number) {
              <li class="card account-row" [attr.data-testid]="'account-order-' + o.number">
                <div>
                  <div class="account-row__badges">
                    @if (o.status === 'CANCELLED') {
                      <span class="badge badge--danger">{{ 'orderStatus.CANCELLED' | t }}</span>
                    } @else if (o.fulfilment) {
                      <span
                        class="badge"
                        [class.badge--success]="o.fulfilment === 'DELIVERED'"
                        [class.badge--accent]="o.fulfilment !== 'DELIVERED'"
                        >{{ 'fulfilment.' + o.fulfilment | t }}</span
                      >
                    }
                    <app-payment-badge [status]="o.paymentStatus" />
                  </div>
                  <h2 dir="ltr" class="mono">{{ o.number }}</h2>
                  <p class="muted">
                    {{ fmt.date(o.createdAt) }} ·
                    {{ 'account.items' | t: { count: fmt.number(o.itemCount) } }} ·
                    {{ 'payment.method.' + o.paymentChoice | t }}
                  </p>
                </div>
                <div class="account-row__actions">
                  <strong class="price">{{ fmt.money(o.total) }}</strong>
                  @if (o.payable) {
                    <a
                      class="btn btn--primary"
                      [routerLink]="['/account/orders', o.number]"
                      data-testid="account-pay"
                      >{{ 'account.payNow' | t }}</a
                    >
                  } @else {
                    <a class="btn" [routerLink]="['/account/orders', o.number]">{{
                      'account.open' | t
                    }}</a>
                  }
                </div>
              </li>
            }
          </ul>
        }
      }
      <p class="section-gap">
        <a routerLink="/shop">{{ 'account.shop' | t }}</a>
      </p>
    </div>
  `,
})
export class AccountPage {
  private readonly api = inject(ApiService);
  private readonly i18n = inject(I18nService);
  protected readonly auth = inject(AuthService);
  protected readonly fmt = inject(FormatService);

  protected readonly orders = rxResource({
    stream: () => this.api.get<ShopOrderRow[]>('/account/orders'),
  });
  protected readonly errorText = computed(() =>
    this.orders.error() ? errorMessage(this.i18n, toApiError(this.orders.error())) : null,
  );
}
