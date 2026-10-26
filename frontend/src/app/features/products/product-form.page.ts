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
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { of } from 'rxjs';

import { ApiError, errorMessage, toApiError } from '../../core/api/api-error';
import { ApiService } from '../../core/api/api.service';
import { Category, Page, Product, Supplier } from '../../core/api/models';
import { FormatService } from '../../core/i18n/format.service';
import { I18nService } from '../../core/i18n/i18n.service';
import { TranslatePipe } from '../../core/i18n/translate.pipe';
import { FieldErrorComponent } from '../../core/ui/field-error.component';
import { IconComponent } from '../../core/ui/icon.component';
import { ToastService } from '../../core/ui/toast.service';

// Same rules as the server (ProductDtos): kept in sync so most mistakes are caught before sending.
// نفس قواعد الخادم (ProductDtos)، ومتزامنة معها حتى ننبه لأغلب الأخطاء قبل الإرسال.
const SKU_PATTERN = /^[A-Za-z0-9-]+$/;
const MONEY_PATTERN = /^\d{1,10}(\.\d{1,2})?$/;

/**
 * Create and edit form for products. The same component handles both: no :id = create, with :id = edit.
 * نموذج إنشاء وتعديل المنتجات. نفس المكوّن للحالتين: بدون :id = إنشاء، ومع :id = تعديل.
 */
@Component({
  selector: 'app-product-form',
  imports: [ReactiveFormsModule, RouterLink, TranslatePipe, FieldErrorComponent, IconComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './product-form.page.html',
})
export class ProductFormPage {
  private readonly api = inject(ApiService);
  private readonly router = inject(Router);
  private readonly i18n = inject(I18nService);
  private readonly toast = inject(ToastService);
  protected readonly fmt = inject(FormatService);

  // Route param; undefined on /products/new.
  // باراميتر الرابط؛ يكون undefined في /products/new.
  readonly id = input<string>();
  protected readonly isEdit = computed(() => !!this.id());

  protected readonly form = inject(NonNullableFormBuilder).group({
    sku: ['', [Validators.required, Validators.maxLength(40), Validators.pattern(SKU_PATTERN)]],
    name: ['', [Validators.required, Validators.maxLength(150)]],
    categoryId: ['', Validators.required],
    supplierId: [''],
    unit: ['piece', [Validators.required, Validators.maxLength(20)]],
    costPrice: ['', [Validators.required, Validators.min(0), Validators.pattern(MONEY_PATTERN)]],
    salePrice: ['', [Validators.required, Validators.min(0), Validators.pattern(MONEY_PATTERN)]],
    reorderLevel: [0, [Validators.required, Validators.min(0), Validators.max(1_000_000)]],
    openingQuantity: [0, [Validators.min(0), Validators.max(1_000_000)]],
    active: [true],
  });

  protected readonly categories = rxResource({
    stream: () => this.api.get<Category[]>('/categories'),
  });
  // Suppliers for the dropdown (first 100 by name is plenty for a select box).
  // الموردين للقائمة المنسدلة (أول 100 بالاسم كافية لقائمة اختيار).
  protected readonly suppliers = rxResource({
    stream: () => this.api.get<Page<Supplier>>('/suppliers', { size: 100 }),
  });
  protected readonly product = rxResource({
    params: () => this.id(),
    stream: ({ params: id }) => (id ? this.api.get<Product>(`/products/${id}`) : of(null)),
  });

  protected readonly saving = signal(false);
  private readonly apiError = signal<ApiError | null>(null);
  protected readonly serverFields = computed(() => this.apiError()?.fields ?? {});
  protected readonly errorText = computed(() => {
    const err = this.apiError();
    return err && err.code !== 'VALIDATION_FAILED' ? errorMessage(this.i18n, err) : null;
  });

  // Live margin preview: (sale − cost) / sale.
  // معاينة هامش الربح مباشرة: (البيع − التكلفة) ÷ البيع.
  private readonly formValue = signal(this.form.getRawValue());
  protected readonly margin = computed(() => {
    const cost = Number(this.formValue().costPrice);
    const sale = Number(this.formValue().salePrice);
    return sale > 0 && !Number.isNaN(cost) ? Math.round(((sale - cost) / sale) * 1000) / 10 : null;
  });

  constructor() {
    // Fill the form once the product is loaded (edit mode).
    // نعبّي النموذج بعد تحميل المنتج (وضع التعديل).
    effect(() => {
      const p = this.product.value();
      if (p) {
        this.form.reset({
          sku: p.sku,
          name: p.name,
          categoryId: String(p.categoryId),
          supplierId: p.supplierId ? String(p.supplierId) : '',
          unit: p.unit,
          costPrice: p.costPrice.toFixed(2),
          salePrice: p.salePrice.toFixed(2),
          reorderLevel: p.reorderLevel,
          openingQuantity: 0,
          active: p.active,
        });
      }
    });
    // Any edit clears old server errors and refreshes the margin preview.
    // أي تعديل يمسح أخطاء الخادم القديمة ويحدّث معاينة الهامش.
    this.form.valueChanges.pipe(takeUntilDestroyed(inject(DestroyRef))).subscribe(() => {
      this.formValue.set(this.form.getRawValue());
      if (this.apiError()?.code === 'VALIDATION_FAILED') this.apiError.set(null);
    });
  }

  protected submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const v = this.form.getRawValue();
    // Build the request body in the exact shape the API expects (numbers, null for "no supplier").
    // نبني جسم الطلب بالشكل اللي يتوقعه الـ API بالضبط (أرقام، وnull لـ "بدون مورد").
    const body = {
      sku: v.sku.trim(),
      name: v.name.trim(),
      categoryId: Number(v.categoryId),
      supplierId: v.supplierId ? Number(v.supplierId) : null,
      unit: v.unit.trim(),
      costPrice: Number(v.costPrice),
      salePrice: Number(v.salePrice),
      reorderLevel: Number(v.reorderLevel),
      ...(this.isEdit()
        ? { active: v.active, version: this.product.value()?.version }
        : { openingQuantity: Number(v.openingQuantity) }),
    };
    this.saving.set(true);
    this.apiError.set(null);
    const request = this.isEdit()
      ? this.api.put<Product>(`/products/${this.id()}`, body)
      : this.api.post<Product>('/products', body);
    request.subscribe({
      next: (saved) => {
        this.toast.success(this.i18n.t(this.isEdit() ? 'common.saved' : 'products.created'));
        // markAsPristine first, so leaving the page does not count as abandoning changes.
        // نعلّم النموذج كمحفوظ أولًا، حتى ما يعتبر الخروج من الصفحة تركًا لتغييرات.
        this.form.markAsPristine();
        void this.router.navigate(['/products', saved.id]);
      },
      error: (err) => {
        this.saving.set(false);
        const apiError = toApiError(err);
        this.apiError.set(apiError);
        if (apiError.code === 'VALIDATION_FAILED') {
          this.form.markAllAsTouched();
          this.toast.error(this.i18n.t('validation.fixForm'));
        }
      },
    });
  }
}
