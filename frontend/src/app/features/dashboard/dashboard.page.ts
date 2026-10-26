import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';

import { toApiError, errorMessage } from '../../core/api/api-error';
import { ApiService } from '../../core/api/api.service';
import { Dashboard } from '../../core/api/models';
import { FormatService } from '../../core/i18n/format.service';
import { I18nService } from '../../core/i18n/i18n.service';
import { TranslatePipe } from '../../core/i18n/translate.pipe';

/**
 * Dashboard: KPIs, a 14-day sales chart, best sellers, reorder list and latest movements, all from one API call.
 * لوحة التحكم: المؤشرات، رسم مبيعات 14 يوم، الأكثر مبيعًا، قائمة إعادة الطلب وآخر الحركات، كلها من طلب واحد.
 */
@Component({
  selector: 'app-dashboard',
  imports: [RouterLink, TranslatePipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './dashboard.page.html',
})
export class DashboardPage {
  private readonly api = inject(ApiService);
  private readonly i18n = inject(I18nService);
  protected readonly fmt = inject(FormatService);

  // rxResource runs the request and exposes value / isLoading / error as signals; reload() repeats it.
  // rxResource ينفذ الطلب ويعرض القيمة وحالة التحميل والخطأ كـ signals، وreload() يعيده.
  protected readonly data = rxResource({ stream: () => this.api.get<Dashboard>('/dashboard') });

  protected readonly errorText = computed(() =>
    this.data.error() ? errorMessage(this.i18n, toApiError(this.data.error())) : null,
  );

  // Bar heights are relative to the best day; Math.max(..., 1) avoids dividing by zero when there are no sales.
  // ارتفاع الأعمدة نسبة لأفضل يوم؛ وMath.max(..., 1) يمنع القسمة على صفر لو ما فيه مبيعات.
  protected readonly bars = computed(() => {
    const days = this.data.value()?.dailySales ?? [];
    const max = Math.max(...days.map((d) => d.total), 1);
    return days.map((d) => ({ ...d, percent: Math.round((d.total / max) * 100) }));
  });

  protected readonly hasSales = computed(() => this.bars().some((b) => b.total > 0));
}
