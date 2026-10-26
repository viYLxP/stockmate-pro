import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  input,
  viewChild,
} from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { Router, RouterLink } from '@angular/router';

import { errorMessage, toApiError } from '../../core/api/api-error';
import { ApiService } from '../../core/api/api.service';
import { Movement, MovementType, Page, Product } from '../../core/api/models';
import { FormatService } from '../../core/i18n/format.service';
import { I18nService } from '../../core/i18n/i18n.service';
import { TranslatePipe } from '../../core/i18n/translate.pipe';
import { IconComponent } from '../../core/ui/icon.component';
import { PaginationComponent } from '../../core/ui/pagination.component';
import { AdjustDialogComponent } from './adjust-dialog.component';

/**
 * Converts a "YYYY-MM-DD" value from a date input into the start of that day in the browser's time zone, as ISO-8601.
 * يحوّل قيمة "YYYY-MM-DD" من حقل التاريخ إلى بداية ذلك اليوم بتوقيت المتصفح، بصيغة ISO-8601.
 *
 * `addDays` lets the "to" filter become the start of the NEXT day, because the API treats "to" as exclusive:
 * picking 2026-10-26 as the end date must still include movements made late on the 26th.
 * `addDays` يخلي فلتر "إلى" يصير بداية اليوم التالي، لأن الـ API يعامل "to" كحد غير شامل:
 * اختيار 2026-10-26 كتاريخ نهاية لازم يشمل الحركات اللي صارت آخر يوم 26.
 */
export function dayStartIso(day: string, addDays = 0): string | null {
  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(day);
  if (!match) return null;
  // Month is zero-based in the Date constructor.
  // الشهر يبدأ من صفر في دالة Date.
  const date = new Date(Number(match[1]), Number(match[2]) - 1, Number(match[3]) + addDays);
  return Number.isNaN(date.getTime()) ? null : date.toISOString();
}

/**
 * The full stock ledger across all products, with filters and a shortcut to adjust the filtered product.
 * سجل المخزون الكامل لكل المنتجات، مع فلاتر واختصار لتسوية المنتج المختار في الفلتر.
 *
 * Filters live in the URL (?productId=&type=&from=&to=&page=) like the product list.
 * الفلاتر محفوظة في الرابط (?productId=&type=&from=&to=&page=) مثل قائمة المنتجات.
 */
@Component({
  selector: 'app-movements',
  imports: [RouterLink, TranslatePipe, IconComponent, PaginationComponent, AdjustDialogComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './movements.page.html',
})
export class MovementsPage {
  private readonly api = inject(ApiService);
  private readonly router = inject(Router);
  private readonly i18n = inject(I18nService);
  protected readonly fmt = inject(FormatService);
  private readonly adjustDialog = viewChild.required(AdjustDialogComponent);

  // A query param missing from the URL arrives as undefined (the router resets it), hence the union type.
  // باراميتر غير موجود في الرابط يوصل كـ undefined (الراوتر يصفّره)، لذلك النوع يشمل undefined.
  readonly productId = input<string | undefined>('');
  readonly type = input<string | undefined>('');
  readonly from = input<string | undefined>('');
  readonly to = input<string | undefined>('');
  readonly page = input<string | undefined>('0');

  protected readonly types: MovementType[] = [
    'OPENING',
    'PURCHASE',
    'SALE',
    'SALE_RETURN',
    'ADJUSTMENT_IN',
    'ADJUSTMENT_OUT',
  ];

  // Archived products are included: their history is still worth filtering by.
  // نضمّن المنتجات المؤرشفة: سجلها ما زال مفيد للفلترة.
  // 100 is the server's max page size; a larger catalogue would need a search-as-you-type picker.
  // 100 هو أقصى حجم صفحة في الخادم؛ لو الكتالوج أكبر نحتاج منتقي بالبحث أثناء الكتابة.
  protected readonly products = rxResource({
    stream: () => this.api.get<Page<Product>>('/products', { size: 100 }),
  });

  protected readonly selectedProduct = computed(() => {
    const id = Number(this.productId());
    return id ? (this.products.value()?.content.find((p) => p.id === id) ?? null) : null;
  });

  // A start date after the end date would return nothing; we warn instead of silently showing an empty table.
  // تاريخ بداية بعد تاريخ النهاية يرجّع لا شيء؛ فننبّه بدل ما نعرض جدول فاضي بصمت.
  protected readonly rangeInvalid = computed(
    () => !!this.from() && !!this.to() && this.from()! > this.to()!,
  );

  protected readonly movements = rxResource({
    params: () =>
      this.rangeInvalid()
        ? undefined
        : {
            productId: this.productId() || null,
            type: this.type() || null,
            from: this.from() ? dayStartIso(this.from()!) : null,
            to: this.to() ? dayStartIso(this.to()!, 1) : null,
            page: Number(this.page() ?? 0) || 0,
            size: 20,
          },
    stream: ({ params }) => this.api.get<Page<Movement>>('/stock/movements', params),
  });

  protected readonly errorText = computed(() =>
    this.movements.error() ? errorMessage(this.i18n, toApiError(this.movements.error())) : null,
  );
  protected readonly hasFilters = computed(
    () => !!(this.productId() || this.type() || this.from() || this.to()),
  );

  /**
   * Updates filters in the URL; any change other than the page number goes back to page 0.
   * يحدّث الفلاتر في الرابط؛ أي تغيير غير رقم الصفحة يرجّع للصفحة الأولى.
   */
  protected setFilter(change: Record<string, string | number | null>): void {
    const pageChange = 'page' in change;
    void this.router.navigate([], {
      queryParams: { ...change, ...(pageChange ? {} : { page: null }) },
      queryParamsHandling: 'merge',
      replaceUrl: !pageChange,
    });
  }

  protected clearFilters(): void {
    void this.router.navigate([], { queryParams: {} });
  }

  protected openAdjust(): void {
    const p = this.selectedProduct();
    if (p) this.adjustDialog().open({ id: p.id, name: p.name, sku: p.sku, quantity: p.quantity });
  }

  // The new movement and the product's quantity both changed, so reload both lists.
  // الحركة الجديدة وكمية المنتج تغيّرتا، فنعيد تحميل القائمتين.
  protected onAdjusted(): void {
    this.movements.reload();
    this.products.reload();
  }
}
