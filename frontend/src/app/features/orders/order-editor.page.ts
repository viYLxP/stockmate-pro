import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  computed,
  effect,
  inject,
  input,
  signal,
} from '@angular/core';
import { rxResource, takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormArray, NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { of } from 'rxjs';

import { ApiError, errorMessage, toApiError } from '../../core/api/api-error';
import { ApiService } from '../../core/api/api.service';
import { Customer, Order, Page, Product, Supplier } from '../../core/api/models';
import { FormatService } from '../../core/i18n/format.service';
import { I18nService } from '../../core/i18n/i18n.service';
import { TranslatePipe } from '../../core/i18n/translate.pipe';
import { computeTotals, lineTotal } from '../../core/money';
import { localPhone } from '../../core/shop';
import { ConfirmService } from '../../core/ui/confirm.service';
import { FieldErrorComponent } from '../../core/ui/field-error.component';
import { IconComponent } from '../../core/ui/icon.component';
import { PromptService } from '../../core/ui/prompt.service';
import { ToastService } from '../../core/ui/toast.service';
import { PaymentBadgeComponent } from '../../shared/payment-badge.component';
import { ORDER_KINDS, OrderKind } from './order-kind';

/**
 * Create, edit, complete (receive / confirm) and cancel an order. Drafts are editable; other statuses are read-only.
 * إنشاء وتعديل وإكمال (استلام / تأكيد) وإلغاء طلب. المسودات قابلة للتعديل، والحالات الأخرى للعرض فقط.
 */
@Component({
  selector: 'app-order-editor',
  imports: [
    ReactiveFormsModule,
    RouterLink,
    TranslatePipe,
    IconComponent,
    FieldErrorComponent,
    PaymentBadgeComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './order-editor.page.html',
})
export class OrderEditorPage {
  private readonly api = inject(ApiService);
  private readonly router = inject(Router);
  private readonly i18n = inject(I18nService);
  private readonly toast = inject(ToastService);
  private readonly confirm = inject(ConfirmService);
  private readonly prompt = inject(PromptService);
  private readonly fb = inject(NonNullableFormBuilder);
  protected readonly fmt = inject(FormatService);

  readonly kind = input.required<OrderKind>();
  readonly id = input<string>();
  protected readonly cfg = computed(() => ORDER_KINDS[this.kind()]);

  protected readonly order = rxResource({
    params: () => ({ id: this.id(), api: this.cfg().api }),
    stream: ({ params }) =>
      params.id ? this.api.get<Order>(`${params.api}/${params.id}`) : of(null),
  });
  // Only active products can be ordered; 100 is the API's maximum page size.
  // فقط المنتجات النشطة يمكن طلبها؛ و100 هو أكبر حجم صفحة يسمح به الـ API.
  protected readonly products = rxResource({
    stream: () => this.api.get<Page<Product>>('/products', { active: true, size: 100 }),
  });
  protected readonly partners = rxResource({
    params: () => this.cfg().partnerApi,
    stream: ({ params }) => this.api.get<Page<Supplier | Customer>>(params, { size: 100 }),
  });

  // New orders and drafts are editable; received/confirmed/cancelled orders are shown read-only.
  // الطلبات الجديدة والمسودات قابلة للتعديل؛ والمستلمة/المؤكدة/الملغية للعرض فقط.
  protected readonly editable = computed(
    () => !this.id() || this.order.value()?.status === 'DRAFT',
  );

  // Shop orders: priced by the shop, then shipped, delivered and paid online or in cash.
  // طلبات المتجر: يسعّرها المتجر، ثم تنشحن وتتوصل وتندفع أونلاين أو كاش.
  protected readonly online = computed(() => this.order.value()?.channel === 'ONLINE');
  // Shipping needs the money first, unless the customer pays the driver.
  // الشحن يحتاج الفلوس أول، إلا لو العميل بيدفع للمندوب.
  protected readonly canShip = computed(() => {
    const o = this.order.value();
    return (
      !!o &&
      o.status === 'CONFIRMED' &&
      o.fulfilment === 'NEW' &&
      (o.paymentStatus === 'PAID' || o.paymentChoice === 'CASH')
    );
  });

  protected readonly form = this.fb.group({
    partnerId: ['', Validators.required],
    notes: ['', Validators.maxLength(500)],
    lines: this.fb.array([this.newLine()]),
  });

  protected readonly busy = signal(false);
  private readonly apiError = signal<ApiError | null>(null);
  protected readonly errorText = computed(() => {
    const err = this.apiError();
    if (!err) return null;
    // Line-level problems (duplicate/inactive product) are shown with their specific message.
    // مشاكل الأسطر (منتج مكرر أو مؤرشف) تظهر برسالتها الخاصة.
    if (err.code === 'VALIDATION_FAILED' && err.fields['lines'])
      return this.i18n.t(`validation.${err.fields['lines']}`);
    return errorMessage(this.i18n, err);
  });

  // Snapshot of the form value as a signal, so totals recompute on every keystroke.
  // نسخة من قيمة النموذج كـ signal، حتى تُعاد حسبة الإجماليات مع كل ضغطة زر.
  private readonly value = signal(this.form.getRawValue());

  protected readonly productMap = computed(
    () => new Map((this.products.value()?.content ?? []).map((p) => [p.id, p])),
  );

  // Live totals preview with the server's rules (see core/money.ts).
  // معاينة مباشرة للإجماليات بقواعد الخادم (شوف core/money.ts).
  protected readonly totals = computed(() => computeTotals(this.value().lines));

  constructor() {
    // When an existing order loads, copy it into the form.
    // لما يتحمّل طلب موجود، ننسخه في النموذج.
    effect(() => {
      const order = this.order.value();
      if (!order) return;
      this.form.controls.lines.clear();
      for (const line of order.lines) {
        this.form.controls.lines.push(
          this.newLine(line.productId, line.quantity, line.unitCost ?? line.unitPrice ?? 0),
        );
      }
      this.form.patchValue({
        partnerId: String(order[this.cfg().partnerIdField] ?? ''),
        notes: order.notes ?? '',
      });
      this.form.markAsPristine();
      this.value.set(this.form.getRawValue());
    });
    // Lock the whole form when the order is not editable, so nothing can be changed by keyboard either.
    // نقفل النموذج كامل لما الطلب غير قابل للتعديل، حتى ما يتغير شي حتى بلوحة المفاتيح.
    effect(() =>
      this.editable()
        ? this.form.enable({ emitEvent: false })
        : this.form.disable({ emitEvent: false }),
    );
    this.form.valueChanges.pipe(takeUntilDestroyed(inject(DestroyRef))).subscribe(() => {
      this.value.set(this.form.getRawValue());
      if (this.apiError()?.code === 'VALIDATION_FAILED') this.apiError.set(null);
    });
  }

  protected get lines(): FormArray {
    return this.form.controls.lines;
  }

  protected addLine(): void {
    this.lines.push(this.newLine());
  }

  protected removeLine(index: number): void {
    // Keep at least one line so the table never disappears.
    // نخلي سطر واحد على الأقل حتى ما يختفي الجدول.
    if (this.lines.length > 1) this.lines.removeAt(index);
  }

  /**
   * When a product is picked, prefill the price with its current cost (purchase) or sale price (sales).
   * لما يُختار منتج، نعبّي السعر تلقائيًا بتكلفته الحالية (شراء) أو سعر بيعه (بيع).
   */
  protected onProductChange(index: number): void {
    const line = this.lines.at(index);
    const product = this.productMap().get(Number(line.value.productId));
    if (product) line.patchValue({ unitPrice: product[this.cfg().priceField].toFixed(2) });
  }

  // For sales: warn when the requested quantity is more than the current stock (the server enforces it on confirm).
  // للبيع: ننبّه لما تكون الكمية المطلوبة أكبر من المخزون الحالي (والخادم يفرضها عند التأكيد).
  protected stockWarning(index: number): boolean {
    if (this.kind() !== 'sales') return false;
    const line = this.value().lines[index];
    const product = line ? this.productMap().get(Number(line.productId)) : undefined;
    return !!product && Number(line.quantity) > product.quantity;
  }

  protected lineTotal(index: number): number {
    const line = this.value().lines[index];
    return line ? lineTotal(line) : 0;
  }

  protected save(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const v = this.form.getRawValue();
    const body = {
      [this.cfg().partnerIdField]: Number(v.partnerId),
      notes: v.notes.trim() || null,
      lines: v.lines.map((l) => ({
        productId: Number(l.productId),
        quantity: Number(l.quantity),
        unitPrice: Number(l.unitPrice),
      })),
    };
    const id = this.id();
    const request = id
      ? this.api.put<Order>(`${this.cfg().api}/${id}`, body)
      : this.api.post<Order>(this.cfg().api, body);
    this.run(request, 'common.saved', (saved) => {
      if (!id) void this.router.navigate([this.cfg().route, saved.id], { replaceUrl: true });
    });
  }

  protected async complete(order: Order): Promise<void> {
    // Unsaved edits would be lost by receiving/confirming the stored version, so ask to save first.
    // التعديلات غير المحفوظة بتضيع لو استلمنا/أكدنا النسخة المحفوظة، فنطلب الحفظ أولًا.
    if (this.form.dirty) {
      this.toast.error(this.i18n.t('common.unsavedChanges'));
      return;
    }
    const ok = await this.confirm.ask({
      title: this.i18n.t('common.confirmTitle'),
      message: this.i18n.t(this.cfg().completeConfirm, { number: order.number }),
      confirmLabel: this.i18n.t(this.cfg().completeLabel),
    });
    if (ok)
      this.run(
        this.api.post<Order>(`${this.cfg().api}/${order.id}/${this.cfg().completeAction}`),
        this.cfg().completeDone,
      );
  }

  protected async cancel(order: Order): Promise<void> {
    if (order.channel === 'ONLINE') {
      // The customer sees the reason, and a paid order is refunded the way it was paid.
      // العميل يشوف السبب، والطلب المدفوع ينرد بنفس طريقة الدفع.
      const reason = await this.prompt.ask({
        title: this.i18n.t('orders.cancelOrder'),
        message:
          order.paymentStatus === 'PAID'
            ? this.i18n.t('orders.cancelPaidHint', {
                number: order.number,
                amount: this.fmt.money(order.total),
              })
            : this.i18n.t('orders.cancelConfirmedHint', { number: order.number }),
        label: this.i18n.t('orders.cancelReason'),
        confirmLabel: this.i18n.t('orders.cancelOrder'),
        maxLength: 200,
        danger: true,
      });
      if (reason !== null)
        this.run(
          this.api.post<Order>(`${this.cfg().api}/${order.id}/cancel`, { reason }),
          'orders.cancelled',
        );
      return;
    }
    const key =
      order.status === 'CONFIRMED' ? 'orders.cancelConfirmedHint' : 'orders.cancelConfirm';
    const ok = await this.confirm.ask({
      title: this.i18n.t('common.confirmTitle'),
      message: this.i18n.t(key, { number: order.number }),
      confirmLabel: this.i18n.t('orders.cancelOrder'),
      danger: true,
    });
    if (ok)
      this.run(this.api.post<Order>(`${this.cfg().api}/${order.id}/cancel`), 'orders.cancelled');
  }

  protected async ship(order: Order): Promise<void> {
    const ok = await this.confirm.ask({
      title: this.i18n.t('common.confirmTitle'),
      message: this.i18n.t('orders.shipConfirm', { number: order.number }),
      confirmLabel: this.i18n.t('orders.ship'),
    });
    if (ok) this.run(this.api.post<Order>(`/sales-orders/${order.id}/ship`), 'orders.shipped');
  }

  protected async deliver(order: Order): Promise<void> {
    // Unpaid cash on delivery: confirming also records the cash the driver brought back.
    // الدفع عند الاستلام وما انْدفع: التأكيد يسجّل الكاش اللي رجع فيه المندوب برضو.
    const cash = order.paymentStatus !== 'PAID';
    const message = [this.i18n.t('orders.deliverConfirm', { number: order.number })];
    if (cash)
      message.push(this.i18n.t('orders.cashCollected', { amount: this.fmt.money(order.total) }));
    const ok = await this.confirm.ask({
      title: this.i18n.t('common.confirmTitle'),
      message: message.join(' '),
      confirmLabel: this.i18n.t('orders.deliver'),
    });
    if (ok)
      this.run(
        this.api.post<Order>(`/sales-orders/${order.id}/deliver`, { cashCollected: cash }),
        'orders.deliveredToast',
      );
  }

  protected async recordCash(order: Order): Promise<void> {
    const ok = await this.confirm.ask({
      title: this.i18n.t('common.confirmTitle'),
      message: this.i18n.t('orders.recordCashConfirm', {
        number: order.number,
        amount: this.fmt.money(order.total),
      }),
      confirmLabel: this.i18n.t('orders.recordCash'),
    });
    if (ok) this.run(this.api.post<Order>(`/sales-orders/${order.id}/cash`), 'orders.cashRecorded');
  }

  protected phone(value: string): string {
    return localPhone(value.replace(/\D/g, ''));
  }

  // Known failure codes are translated; a gateway's own code is shown as it came.
  // رموز الفشل المعروفة تتترجم؛ ورمز البوابة الخاص ينعرض مثل ما جا.
  protected failure(reason: string): string {
    const key = `payment.reason.${reason}`;
    const text = this.i18n.t(key);
    return text === key ? reason : text;
  }

  // The system's own cancel reasons are translated; a reason typed by staff is shown as written.
  // أسباب الإلغاء من النظام تتترجم؛ والسبب اللي كتبه الموظف ينعرض مثل ما هو.
  protected reasonText(reason: string): string {
    return reason === 'CUSTOMER' || reason === 'PAYMENT_TIMEOUT'
      ? this.i18n.t(`orders.reason.${reason}`)
      : reason;
  }

  // Shared request handling: busy flag, success toast, reload, and error display.
  // معالجة مشتركة للطلبات: حالة الانشغال، رسالة النجاح، إعادة التحميل، وعرض الخطأ.
  private run(
    request: ReturnType<ApiService['post']>,
    successKey: string,
    after?: (order: Order) => void,
  ): void {
    this.busy.set(true);
    this.apiError.set(null);
    request.subscribe({
      next: (result) => {
        this.busy.set(false);
        this.toast.success(this.i18n.t(successKey));
        this.form.markAsPristine();
        this.order.reload();
        // Stock changed, so refresh "available" quantities too.
        // المخزون تغيّر، فنحدّث الكميات المتاحة أيضًا.
        this.products.reload();
        after?.(result as Order);
      },
      error: (err) => {
        this.busy.set(false);
        this.apiError.set(toApiError(err));
      },
    });
  }

  private newLine(productId: number | '' = '', quantity = 1, unitPrice: number | string = '') {
    return this.fb.group({
      productId: [productId === '' ? '' : String(productId), Validators.required],
      quantity: [quantity, [Validators.required, Validators.min(1), Validators.max(1_000_000)]],
      unitPrice: [
        typeof unitPrice === 'number' ? unitPrice.toFixed(2) : unitPrice,
        [Validators.required, Validators.min(0), Validators.pattern(/^\d{1,10}(\.\d{1,2})?$/)],
      ],
    });
  }
}
