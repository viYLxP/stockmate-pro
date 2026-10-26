import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  input,
  signal,
  viewChild,
} from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';

import { ApiService } from '../../core/api/api.service';
import { Movement, Page, Product } from '../../core/api/models';
import { FormatService } from '../../core/i18n/format.service';
import { TranslatePipe } from '../../core/i18n/translate.pipe';
import { IconComponent } from '../../core/ui/icon.component';
import { PaginationComponent } from '../../core/ui/pagination.component';
import { AdjustDialogComponent } from '../stock/adjust-dialog.component';

/**
 * Product details with its full stock ledger and a shortcut to adjust stock.
 * تفاصيل المنتج مع دفتر حركاته الكامل واختصار لتسوية المخزون.
 */
@Component({
  selector: 'app-product-detail',
  imports: [RouterLink, TranslatePipe, IconComponent, PaginationComponent, AdjustDialogComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './product-detail.page.html',
})
export class ProductDetailPage {
  private readonly api = inject(ApiService);
  protected readonly fmt = inject(FormatService);

  readonly id = input.required<string>();
  protected readonly movementsPage = signal(0);
  private readonly adjustDialog = viewChild.required(AdjustDialogComponent);

  protected readonly product = rxResource({
    params: () => this.id(),
    stream: ({ params: id }) => this.api.get<Product>(`/products/${id}`),
  });
  protected readonly movements = rxResource({
    params: () => ({ productId: this.id(), page: this.movementsPage(), size: 10 }),
    stream: ({ params }) => this.api.get<Page<Movement>>('/stock/movements', params),
  });

  // Margin = (sale − cost) / sale, shown as a percentage with one decimal.
  // الهامش = (البيع − التكلفة) ÷ البيع، ويُعرض كنسبة مئوية بمنزلة عشرية وحدة.
  protected readonly margin = computed(() => {
    const p = this.product.value();
    return p && p.salePrice > 0
      ? Math.round(((p.salePrice - p.costPrice) / p.salePrice) * 1000) / 10
      : null;
  });

  protected openAdjust(p: Product): void {
    this.adjustDialog().open({ id: p.id, name: p.name, sku: p.sku, quantity: p.quantity });
  }

  // After an adjustment both the quantity and the history changed, so reload both.
  // بعد التسوية تغيّرت الكمية والسجل، فنعيد تحميل الاثنين.
  protected onAdjusted(): void {
    this.product.reload();
    this.movementsPage.set(0);
    this.movements.reload();
  }
}
