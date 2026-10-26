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
import { Category, Page, Product, StockStatus } from '../../core/api/models';
import { AuthService } from '../../core/auth/auth.service';
import { FormatService } from '../../core/i18n/format.service';
import { I18nService } from '../../core/i18n/i18n.service';
import { TranslatePipe } from '../../core/i18n/translate.pipe';
import { ConfirmService } from '../../core/ui/confirm.service';
import { IconComponent } from '../../core/ui/icon.component';
import { PaginationComponent } from '../../core/ui/pagination.component';
import { ToastService } from '../../core/ui/toast.service';

/**
 * Product list with search, filters and server-side pagination.
 * قائمة المنتجات مع البحث والفلاتر والتقسيم إلى صفحات من جهة الخادم.
 *
 * Filters live in the URL query string (?q=&status=&page=), so a filtered view can be bookmarked or shared,
 * and the browser Back button restores the previous filters.
 * الفلاتر محفوظة في الرابط (?q=&status=&page=)، فتقدر تحفظ أو تشارك عرض مفلتر،
 * وزر الرجوع في المتصفح يرجّع الفلاتر السابقة.
 */
@Component({
  selector: 'app-product-list',
  imports: [RouterLink, TranslatePipe, IconComponent, PaginationComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './product-list.page.html',
})
export class ProductListPage {
  private readonly api = inject(ApiService);
  private readonly router = inject(Router);
  private readonly i18n = inject(I18nService);
  private readonly toast = inject(ToastService);
  private readonly confirm = inject(ConfirmService);
  protected readonly auth = inject(AuthService);
  protected readonly fmt = inject(FormatService);

  // Query params bound as inputs by withComponentInputBinding().
  // باراميترات الرابط تصل كـ inputs بفضل withComponentInputBinding().
  // A query param missing from the URL arrives as undefined (the router resets it), hence the union type.
  // باراميتر غير موجود في الرابط يوصل كـ undefined (الراوتر يصفّره)، لذلك النوع يشمل undefined.
  readonly q = input<string | undefined>('');
  readonly categoryId = input<string | undefined>('');
  readonly status = input<string | undefined>('');
  readonly archived = input<string | undefined>('');
  readonly page = input<string | undefined>('0');

  // The search box updates instantly while typing; linkedSignal resets it when the URL changes (e.g. Back button).
  // مربع البحث يتحدث فورًا أثناء الكتابة، وlinkedSignal يرجّعه لقيمة الرابط لو تغيّر (مثل زر الرجوع).
  protected readonly searchText = linkedSignal(() => this.q() ?? '');

  protected readonly categories = rxResource({
    stream: () => this.api.get<Category[]>('/categories'),
  });

  protected readonly products = rxResource({
    params: () => ({
      q: this.q(),
      categoryId: this.categoryId(),
      status: this.status(),
      // Default view hides archived products; the checkbox shows them all.
      // العرض الافتراضي يخفي المؤرشف، والخانة تعرض الكل.
      active: this.archived() === '1' ? null : true,
      page: Number(this.page() ?? 0) || 0,
      // No sort param: the server's default (name, then id) gives a stable order across pages.
      // بدون باراميتر ترتيب: الافتراضي في الخادم (الاسم ثم id) يعطي ترتيب ثابت بين الصفحات.
      size: 20,
    }),
    stream: ({ params }) => this.api.get<Page<Product>>('/products', params),
  });

  protected readonly errorText = computed(() =>
    this.products.error() ? errorMessage(this.i18n, toApiError(this.products.error())) : null,
  );
  protected readonly hasFilters = computed(
    () => !!(this.q() || this.categoryId() || this.status()),
  );
  protected readonly statuses: StockStatus[] = ['OK', 'LOW', 'OUT'];

  private searchTimer: ReturnType<typeof setTimeout> | undefined;

  /**
   * Debounced search: wait 300 ms after the last keystroke before updating the URL (and calling the API),
   * so typing "rice" sends one request instead of four.
   * بحث مؤجل: ننتظر 300 ملي ثانية بعد آخر حرف قبل تحديث الرابط (واستدعاء الـ API)،
   * فكتابة "rice" ترسل طلب واحد بدل أربعة.
   */
  protected onSearch(value: string): void {
    this.searchText.set(value);
    clearTimeout(this.searchTimer);
    this.searchTimer = setTimeout(() => this.setFilter({ q: value.trim() }), 300);
  }

  /**
   * Updates one or more filters in the URL. Any filter change goes back to the first page.
   * يحدّث فلتر أو أكثر في الرابط. أي تغيير في الفلاتر يرجّع للصفحة الأولى.
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
    this.searchText.set('');
    void this.router.navigate([], { queryParams: {} });
  }

  protected async remove(product: Product): Promise<void> {
    const ok = await this.confirm.ask({
      title: this.i18n.t('common.confirmTitle'),
      message: this.i18n.t('common.deleteConfirm', { name: product.name }),
      confirmLabel: this.i18n.t('common.delete'),
      danger: true,
    });
    if (!ok) return;
    this.api.delete(`/products/${product.id}`).subscribe({
      next: () => {
        this.toast.success(this.i18n.t('common.deleted'));
        this.products.reload();
      },
      // IN_USE here means the product has history; the message suggests archiving instead.
      // IN_USE هنا يعني إن للمنتج سجل، والرسالة تقترح الأرشفة بدل الحذف.
      error: (err) => {
        const apiError = toApiError(err);
        this.toast.error(
          apiError.code === 'IN_USE'
            ? this.i18n.t('products.archiveHint')
            : errorMessage(this.i18n, apiError),
        );
      },
    });
  }
}
