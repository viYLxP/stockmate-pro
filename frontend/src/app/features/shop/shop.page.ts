import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  input,
  linkedSignal,
} from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { Router, RouterLink } from '@angular/router';

import { errorMessage, toApiError } from '../../core/api/api-error';
import { ApiService } from '../../core/api/api.service';
import { Page, ShopCategory, ShopProduct } from '../../core/api/models';
import { CartService } from '../../core/cart.service';
import { FormatService } from '../../core/i18n/format.service';
import { I18nService } from '../../core/i18n/i18n.service';
import { TranslatePipe } from '../../core/i18n/translate.pipe';
import { localized, tileHue } from '../../core/shop';
import { IconComponent } from '../../core/ui/icon.component';
import { PaginationComponent } from '../../core/ui/pagination.component';
import { ToastService } from '../../core/ui/toast.service';

export const SORTS = ['NEWEST', 'PRICE_ASC', 'PRICE_DESC', 'NAME'] as const;

/**
 * The shop's catalogue: categories, search, sort and pages, all kept in the URL so a view can be shared.
 * كتالوج المتجر: التصنيفات والبحث والترتيب والصفحات، كلها في الرابط حتى ينشارك العرض.
 */
@Component({
  selector: 'app-shop',
  imports: [RouterLink, TranslatePipe, IconComponent, PaginationComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './shop.page.html',
})
export class ShopPage {
  private readonly api = inject(ApiService);
  private readonly router = inject(Router);
  private readonly toast = inject(ToastService);
  protected readonly i18n = inject(I18nService);
  protected readonly fmt = inject(FormatService);
  protected readonly cart = inject(CartService);

  readonly q = input<string | undefined>('');
  readonly category = input<string | undefined>('');
  readonly sort = input<string | undefined>('NEWEST');
  readonly page = input<string | undefined>('0');

  protected readonly searchText = linkedSignal(() => this.q() ?? '');
  protected readonly sorts = SORTS;
  protected readonly currentSort = computed(() =>
    (SORTS as readonly string[]).includes(this.sort() ?? '') ? this.sort()! : 'NEWEST',
  );

  protected readonly categories = rxResource({
    stream: () => this.api.get<ShopCategory[]>('/shop/categories'),
  });
  protected readonly products = rxResource({
    params: () => ({
      q: this.q(),
      categoryId: this.category(),
      sort: this.currentSort(),
      page: Number(this.page() ?? 0) || 0,
      size: 12,
    }),
    stream: ({ params }) => this.api.get<Page<ShopProduct>>('/shop/products', params),
  });
  protected readonly errorText = computed(() =>
    this.products.error() ? errorMessage(this.i18n, toApiError(this.products.error())) : null,
  );
  protected readonly firstPage = computed(
    () => !this.q() && !this.category() && !(Number(this.page() ?? 0) > 0),
  );

  private searchTimer: ReturnType<typeof setTimeout> | undefined;

  protected onSearch(value: string): void {
    this.searchText.set(value);
    clearTimeout(this.searchTimer);
    this.searchTimer = setTimeout(() => this.setFilter({ q: value.trim() || null }), 300);
  }

  protected setFilter(change: Record<string, string | number | null>): void {
    const pageChange = 'page' in change;
    void this.router.navigate([], {
      queryParams: { ...change, ...(pageChange ? {} : { page: null }) },
      queryParamsHandling: 'merge',
      replaceUrl: !pageChange,
    });
  }

  protected name(en: string, ar: string | null): string {
    return localized(this.i18n.lang(), en, ar);
  }

  protected hue(categoryId: number): number {
    return tileHue(categoryId);
  }

  protected add(product: ShopProduct): void {
    if (this.cart.add(product)) {
      this.toast.success(this.i18n.t('shop.added'));
    } else {
      this.toast.error(this.i18n.t('shop.cartFull'));
    }
  }
}
