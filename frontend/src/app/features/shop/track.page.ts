import {
  ChangeDetectionStrategy,
  Component,
  OnInit,
  computed,
  inject,
  input,
  signal,
} from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { Observable } from 'rxjs';

import { ApiError, errorMessage, toApiError } from '../../core/api/api-error';
import { ApiService } from '../../core/api/api.service';
import { CheckoutResponse, PaymentMethod, ShopOrder } from '../../core/api/models';
import { AuthService } from '../../core/auth/auth.service';
import { openCheckout } from '../../core/checkout';
import { FormatService } from '../../core/i18n/format.service';
import { I18nService } from '../../core/i18n/i18n.service';
import { TranslatePipe } from '../../core/i18n/translate.pipe';
import { ORDER_NUMBER, SAUDI_MOBILE, normalizeOrderNumber } from '../../core/shop';
import { ConfirmService } from '../../core/ui/confirm.service';
import { FieldErrorComponent } from '../../core/ui/field-error.component';
import { ToastService } from '../../core/ui/toast.service';
import { OrderViewComponent } from '../../shared/order-view.component';

/**
 * Guest tracking: order number + the mobile number it was placed with, then the order, payment and cancel.
 * Both values are needed, so an order number seen by someone else shows nothing.
 * تتبع الزائر: رقم الطلب + الجوال اللي انطلب فيه، ثم الطلب والدفع والإلغاء.
 * القيمتين مطلوبة، فرقم الطلب لو شافه أحد ثاني ما يعرض شي.
 */
@Component({
  selector: 'app-track',
  imports: [
    ReactiveFormsModule,
    RouterLink,
    TranslatePipe,
    FieldErrorComponent,
    OrderViewComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="page-narrow">
      @if (order(); as o) {
        @if (errorText(); as message) {
          <div class="alert alert--danger" role="alert" data-testid="track-error">
            {{ message }}
          </div>
        }
        <app-order-view [order]="o" [busy]="busy()" (pay)="pay($event)" (cancelOrder)="cancel()" />
        <p class="section-gap">
          <button type="button" class="btn" (click)="reset()" data-testid="track-another">
            {{ 'track.another' | t }}
          </button>
        </p>
      } @else {
        <header class="page-header page-header--center">
          <div>
            <h1>{{ 'track.title' | t }}</h1>
            <p class="muted">{{ 'track.subtitle' | t }}</p>
          </div>
        </header>
        <form class="card track-form" [formGroup]="form" (ngSubmit)="submit()" novalidate>
          @if (errorText(); as message) {
            <div class="alert alert--danger" role="alert" data-testid="track-error">
              {{ message }}
            </div>
          }
          <div class="field">
            <label for="t-number">{{ 'track.number' | t }}</label>
            <input
              id="t-number"
              formControlName="number"
              dir="ltr"
              autocomplete="off"
              autocapitalize="characters"
              spellcheck="false"
              maxlength="20"
              [placeholder]="'track.numberHint' | t"
              aria-describedby="t-number-err"
              data-testid="track-number"
            />
            <app-field-error
              [control]="form.controls.number"
              patternKey="validation.OrderNumber"
              id="t-number-err"
            />
          </div>
          <div class="field">
            <label for="t-phone">{{ 'track.phone' | t }}</label>
            <input
              id="t-phone"
              type="tel"
              inputmode="tel"
              formControlName="phone"
              dir="ltr"
              maxlength="20"
              autocomplete="tel"
              placeholder="05XXXXXXXX"
              aria-describedby="t-phone-err"
              data-testid="track-phone"
            />
            <app-field-error
              [control]="form.controls.phone"
              patternKey="validation.Phone"
              id="t-phone-err"
            />
          </div>
          <button
            type="submit"
            class="btn btn--primary btn--block"
            [disabled]="busy()"
            data-testid="track-submit"
          >
            {{ (busy() ? 'track.searching' : 'track.submit') | t }}
          </button>
          @if (auth.isCustomer()) {
            <p class="hint">
              <a routerLink="/account">{{ 'shop.account' | t }}</a>
            </p>
          } @else {
            <p class="hint">
              {{ 'track.accountHint' | t }}
              <a routerLink="/register">{{ 'shop.register' | t }}</a>
            </p>
          }
        </form>
      }
    </div>
  `,
})
export class TrackPage implements OnInit {
  private readonly api = inject(ApiService);
  private readonly router = inject(Router);
  private readonly toast = inject(ToastService);
  private readonly confirm = inject(ConfirmService);
  private readonly fmt = inject(FormatService);
  protected readonly i18n = inject(I18nService);
  protected readonly auth = inject(AuthService);

  // ?number=SO-… from the thank-you screen or the payment page fills the number.
  // ‎?number=SO-… من شاشة الشكر أو صفحة الدفع يعبّي رقم الطلب.
  readonly number = input<string | undefined>();

  protected readonly form = inject(NonNullableFormBuilder).group({
    number: ['', [Validators.required, Validators.pattern(ORDER_NUMBER)]],
    phone: ['', [Validators.required, Validators.pattern(SAUDI_MOBILE)]],
  });

  protected readonly order = signal<ShopOrder | null>(null);
  protected readonly busy = signal(false);
  private readonly apiError = signal<ApiError | null>(null);
  // The phone that found the order; pay and cancel send the same one.
  // الجوال اللي لقى الطلب؛ والدفع والإلغاء يرسلون نفسه.
  private phone = '';

  protected readonly errorText = computed(() => {
    const err = this.apiError();
    if (!err) return null;
    return err.code === 'NOT_FOUND' ? this.i18n.t('track.notFound') : errorMessage(this.i18n, err);
  });

  ngOnInit(): void {
    const number = this.number();
    if (number) this.form.controls.number.setValue(number);
  }

  protected submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const number = normalizeOrderNumber(this.form.controls.number.value);
    const phone = this.form.controls.phone.value.trim();
    this.run(this.api.post<ShopOrder>('/shop/orders/track', { number, phone }), () => {
      this.phone = phone;
    });
  }

  protected pay(method: PaymentMethod): void {
    const o = this.order();
    if (!o) return;
    this.busy.set(true);
    this.apiError.set(null);
    this.api
      .post<CheckoutResponse>(`/shop/orders/${o.number}/pay`, { phone: this.phone, method })
      .subscribe({
        next: (checkout) => openCheckout(this.router, checkout),
        error: (err) => {
          this.busy.set(false);
          this.apiError.set(toApiError(err));
        },
      });
  }

  protected async cancel(): Promise<void> {
    const o = this.order();
    if (!o) return;
    const paid = o.payment.status === 'PAID';
    const ok = await this.confirm.ask({
      title: this.i18n.t('common.confirmTitle'),
      message: paid
        ? this.i18n.t('order.cancelPaidConfirm', {
            number: o.number,
            amount: this.fmt.money(o.total),
          })
        : this.i18n.t('order.cancelConfirm', { number: o.number }),
      confirmLabel: this.i18n.t('order.cancel'),
      danger: true,
    });
    if (!ok) return;
    this.run(
      this.api.post<ShopOrder>(`/shop/orders/${o.number}/cancel`, { phone: this.phone }),
      () => this.toast.success(this.i18n.t('order.cancelled')),
    );
  }

  protected reset(): void {
    this.order.set(null);
    this.apiError.set(null);
    this.phone = '';
    this.form.reset();
  }

  private run(request: Observable<ShopOrder>, onSuccess?: () => void): void {
    this.busy.set(true);
    this.apiError.set(null);
    request.subscribe({
      next: (order) => {
        this.busy.set(false);
        this.order.set(order);
        onSuccess?.();
      },
      error: (err) => {
        this.busy.set(false);
        this.apiError.set(toApiError(err));
      },
    });
  }
}
