import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  inject,
  signal,
} from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';

import { ApiError, errorMessage, toApiError } from '../../core/api/api-error';
import { ApiService } from '../../core/api/api.service';
import { CITIES, CheckoutResult, City, PaymentMethod, Quote } from '../../core/api/models';
import { AuthService } from '../../core/auth/auth.service';
import { CartService } from '../../core/cart.service';
import { openCheckout } from '../../core/checkout';
import { FormatService } from '../../core/i18n/format.service';
import { I18nService } from '../../core/i18n/i18n.service';
import { TranslatePipe } from '../../core/i18n/translate.pipe';
import {
  INSTALMENTS_MAX,
  INSTALMENTS_MIN,
  SAUDI_MOBILE,
  localPhone,
  localized,
  withVat,
} from '../../core/shop';
import { FieldErrorComponent } from '../../core/ui/field-error.component';
import { IconComponent } from '../../core/ui/icon.component';

const METHODS: PaymentMethod[] = ['CARD', 'APPLE_PAY', 'TABBY', 'TAMARA', 'CASH'];
const ICONS: Record<PaymentMethod, string> = {
  CARD: 'card',
  APPLE_PAY: 'phone',
  TABBY: 'split',
  TAMARA: 'split',
  CASH: 'cash',
};

/**
 * Checkout: contact (guests only), delivery address and payment method. The server prices the order again,
 * takes the stock and, for online methods, opens the payment page; cash orders end on a thank-you screen.
 * إتمام الطلب: بيانات التواصل (للزائر بس)، وعنوان التوصيل، وطريقة الدفع. الخادم يسعّر الطلب من جديد،
 * وياخذ المخزون، وللدفع الأونلاين يفتح صفحة الدفع؛ وطلبات الكاش تنتهي بشاشة شكر.
 */
@Component({
  selector: 'app-checkout',
  imports: [ReactiveFormsModule, RouterLink, TranslatePipe, IconComponent, FieldErrorComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './checkout.page.html',
})
export class CheckoutPage {
  private readonly api = inject(ApiService);
  private readonly router = inject(Router);
  protected readonly i18n = inject(I18nService);
  protected readonly fmt = inject(FormatService);
  protected readonly auth = inject(AuthService);
  protected readonly cart = inject(CartService);

  protected readonly cities = CITIES;
  protected readonly methods = METHODS;
  protected readonly instalmentsRange = { min: INSTALMENTS_MIN, max: INSTALMENTS_MAX };

  protected readonly form = inject(NonNullableFormBuilder).group({
    fullName: ['', [Validators.required, Validators.maxLength(120)]],
    phone: ['', [Validators.required, Validators.pattern(SAUDI_MOBILE)]],
    email: ['', [Validators.email, Validators.maxLength(150)]],
    city: ['RIYADH' as City, Validators.required],
    district: ['', [Validators.required, Validators.maxLength(100)]],
    address: ['', [Validators.required, Validators.maxLength(300)]],
    paymentMethod: ['CARD' as PaymentMethod, Validators.required],
    // Honeypot, hidden from people. / مصيدة، مخفية عن الناس.
    website: [''],
  });

  protected readonly quote = rxResource({
    params: () => (this.cart.isEmpty() ? undefined : this.cart.items()),
    stream: ({ params }) => this.api.post<Quote>('/shop/quote', { items: params }),
  });
  protected readonly method = signal<PaymentMethod>('CARD');
  protected readonly instalmentsOk = computed(() => {
    const total = this.quote.value()?.total ?? 0;
    return total >= INSTALMENTS_MIN && total <= INSTALMENTS_MAX;
  });

  protected readonly submitting = signal(false);
  protected readonly placed = signal<CheckoutResult | null>(null);
  private readonly apiError = signal<ApiError | null>(null);
  protected readonly errorText = computed(() => {
    const err = this.apiError();
    return err && err.code !== 'VALIDATION_FAILED' ? errorMessage(this.i18n, err) : null;
  });
  // A cart problem is fixed on the cart page, so the message links there. / مشكلة السلة تنحل في صفحة السلة، فالرسالة ترتبط فيها.
  protected readonly cartProblem = computed(() =>
    ['PRODUCT_UNAVAILABLE', 'INSUFFICIENT_STOCK'].includes(this.apiError()?.code ?? ''),
  );
  protected readonly serverFields = computed(() => this.apiError()?.fields ?? {});

  constructor() {
    // A customer's name and phone come from the account, so those fields are not asked.
    // اسم وجوال العميل من حسابه، فما نطلب هالحقول.
    effect(() => {
      const user = this.auth.user();
      const { fullName, phone, email } = this.form.controls;
      if (this.auth.isCustomer() && user) {
        fullName.disable();
        phone.disable();
        if (!email.value && user.email) email.setValue(user.email);
      } else {
        fullName.enable();
        phone.enable();
      }
    });
    // Instalments outside their range fall back to card. / التقسيط برا حدوده يرجع للبطاقة.
    effect(() => {
      const m = this.method();
      if ((m === 'TABBY' || m === 'TAMARA') && this.quote.hasValue() && !this.instalmentsOk()) {
        this.choose('CARD');
      }
    });
  }

  protected icon(m: PaymentMethod): string {
    return ICONS[m];
  }

  protected disabled(m: PaymentMethod): boolean {
    return (m === 'TABBY' || m === 'TAMARA') && !this.instalmentsOk();
  }

  protected choose(m: PaymentMethod): void {
    if (this.disabled(m)) return;
    this.method.set(m);
    this.form.controls.paymentMethod.setValue(m);
  }

  protected name(en: string | null, ar: string | null): string {
    return localized(this.i18n.lang(), en, ar);
  }

  // Summary lines include VAT like the shop; the totals below are the server's.
  // أسطر الملخص شاملة الضريبة مثل المتجر؛ والمجاميع تحت من الخادم.
  protected vat(amount: number): number {
    return withVat(amount);
  }

  protected phone(value: string | null | undefined): string {
    return localPhone(value);
  }

  protected submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const v = this.form.getRawValue();
    this.submitting.set(true);
    this.apiError.set(null);
    this.api
      .post<CheckoutResult>('/shop/checkout', {
        items: this.cart.items(),
        fullName: this.auth.isCustomer() ? null : v.fullName.trim(),
        phone: this.auth.isCustomer() ? null : v.phone.trim(),
        email: v.email.trim() || null,
        city: v.city,
        district: v.district.trim(),
        address: v.address.trim(),
        paymentMethod: v.paymentMethod,
        website: v.website,
      })
      .subscribe({
        next: (result) => {
          // The order exists now, whatever happens next, so the cart is emptied.
          // الطلب انحفظ الحين مهما صار بعدها، فالسلة تتفضى.
          this.cart.clear();
          if (result.paymentId && result.checkoutUrl) {
            openCheckout(this.router, {
              paymentId: result.paymentId,
              checkoutUrl: result.checkoutUrl,
            });
          } else {
            this.submitting.set(false);
            this.placed.set(result);
            window.scrollTo({ top: 0 });
          }
        },
        error: (err) => {
          this.submitting.set(false);
          const error = toApiError(err);
          this.apiError.set(error);
          if (this.cartProblem()) this.quote.reload();
        },
      });
  }
}
