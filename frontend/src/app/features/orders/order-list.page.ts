import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  input,
  linkedSignal,
  signal,
} from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';

import { ApiService } from '../../core/api/api.service';
import { OrderSummary, Page } from '../../core/api/models';
import { FormatService } from '../../core/i18n/format.service';
import { TranslatePipe } from '../../core/i18n/translate.pipe';
import { IconComponent } from '../../core/ui/icon.component';
import { PaginationComponent } from '../../core/ui/pagination.component';
import { PaymentBadgeComponent } from '../../shared/payment-badge.component';
import { ORDER_KINDS, OrderKind } from './order-kind';

/**
 * Purchase or sales order list (decided by route data.kind), filterable by status and order number.
 * قائمة طلبات الشراء أو البيع (حسب data.kind في المسار)، مع تصفية بالحالة ورقم الطلب.
 */
@Component({
  selector: 'app-order-list',
  imports: [RouterLink, TranslatePipe, IconComponent, PaginationComponent, PaymentBadgeComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './order-list.page.html',
})
export class OrderListPage {
  private readonly api = inject(ApiService);
  protected readonly fmt = inject(FormatService);

  readonly kind = input.required<OrderKind>();
  protected readonly cfg = computed(() => ORDER_KINDS[this.kind()]);

  // ?channel=ONLINE opens the shop orders directly (the dashboard links here).
  // ‎?channel=ONLINE يفتح طلبات المتجر مباشرة (اللوحة ترتبط هنا).
  readonly channel = input<string | undefined>();
  protected readonly channelFilter = linkedSignal(() =>
    this.channel() === 'ONLINE' || this.channel() === 'OFFICE' ? this.channel()! : '',
  );

  protected readonly status = signal('');
  protected readonly search = signal('');
  protected readonly page = signal(0);

  protected readonly orders = rxResource({
    params: () => ({
      api: this.cfg().api,
      status: this.status(),
      // Purchases have no channel. / المشتريات ما لها قناة.
      channel: this.kind() === 'sales' ? this.channelFilter() : '',
      q: this.search(),
      page: this.page(),
      size: 20,
    }),
    stream: ({ params: { api, ...query } }) => this.api.get<Page<OrderSummary>>(api, query),
  });

  private searchTimer: ReturnType<typeof setTimeout> | undefined;

  protected onSearch(value: string): void {
    clearTimeout(this.searchTimer);
    this.searchTimer = setTimeout(() => {
      this.page.set(0);
      this.search.set(value.trim());
    }, 300);
  }

  protected setChannel(value: string): void {
    this.page.set(0);
    this.channelFilter.set(value);
  }

  protected setStatus(value: string): void {
    this.page.set(0);
    this.status.set(value);
  }

  // Reads the partner name from whichever field this kind uses (supplierName or customerName).
  // يقرأ اسم الطرف الآخر من الحقل المناسب لهذا النوع (supplierName أو customerName).
  protected partnerName(order: OrderSummary): string {
    return order[this.cfg().partnerNameField] ?? '—';
  }
}
