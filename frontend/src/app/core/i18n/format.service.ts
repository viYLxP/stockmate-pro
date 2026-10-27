import { Injectable, computed, inject } from '@angular/core';

import { I18nService } from './i18n.service';

/**
 * Locale-aware number, money and date formatting with the browser's Intl API.
 * تنسيق الأرقام والمبالغ والتواريخ حسب اللغة باستخدام Intl المدمج في المتصفح.
 *
 * Formatters are created once per language (computed) because building Intl objects is relatively expensive.
 * ننشئ أدوات التنسيق مرة وحدة لكل لغة (computed) لأن إنشاء كائنات Intl مكلف نسبيًا.
 */
@Injectable({ providedIn: 'root' })
export class FormatService {
  private readonly i18n = inject(I18nService);

  private readonly moneyFmt = computed(
    () =>
      new Intl.NumberFormat(this.i18n.locale(), {
        minimumFractionDigits: 2,
        maximumFractionDigits: 2,
      }),
  );
  private readonly numberFmt = computed(() => new Intl.NumberFormat(this.i18n.locale()));
  private readonly dateFmt = computed(
    () =>
      new Intl.DateTimeFormat(this.i18n.locale(), { dateStyle: 'medium', timeZone: 'Asia/Riyadh' }),
  );
  private readonly dateTimeFmt = computed(
    () =>
      new Intl.DateTimeFormat(this.i18n.locale(), {
        dateStyle: 'medium',
        timeStyle: 'short',
        timeZone: 'Asia/Riyadh',
      }),
  );
  private readonly dayFmt = computed(
    () =>
      new Intl.DateTimeFormat(this.i18n.locale(), {
        day: 'numeric',
        month: 'short',
        timeZone: 'UTC',
      }),
  );

  private readonly plainDateFmt = computed(
    () => new Intl.DateTimeFormat(this.i18n.locale(), { dateStyle: 'medium', timeZone: 'UTC' }),
  );

  /** "1,234.50 SAR" / "1,234.50 ر.س" */
  money(value: number | null | undefined): string {
    return `${this.moneyFmt().format(value ?? 0)} ${this.i18n.t('app.currency')}`;
  }

  /** Number with 2 decimals, without currency (for table cells). / رقم بمنزلتين بدون عملة (لخانات الجداول). */
  amount(value: number | null | undefined): string {
    return this.moneyFmt().format(value ?? 0);
  }

  number(value: number | null | undefined): string {
    return this.numberFmt().format(value ?? 0);
  }

  /** Server timestamps are UTC; we always show them in Riyadh time. / أوقات الخادم بتوقيت UTC ونعرضها دائمًا بتوقيت الرياض. */
  date(iso: string | null | undefined): string {
    return iso ? this.dateFmt().format(new Date(iso)) : '—';
  }

  dateTime(iso: string | null | undefined): string {
    return iso ? this.dateTimeFmt().format(new Date(iso)) : '—';
  }

  /**
   * Short label for a calendar day such as "2026-10-26" (no time zone shift, the server already grouped by local day).
   * تسمية قصيرة ليوم مثل "2026-10-26" (بدون إزاحة توقيت، لأن الخادم جمّع حسب اليوم المحلي مسبقًا).
   */
  day(isoDate: string): string {
    return this.dayFmt().format(new Date(`${isoDate}T00:00:00Z`));
  }

  /** A calendar date from the server ("2026-11-27"), shown without a time-zone shift. / تاريخ تقويم بدون إزاحة توقيت. */
  plainDate(isoDate: string | null | undefined): string {
    return isoDate ? this.plainDateFmt().format(new Date(`${isoDate}T00:00:00Z`)) : '—';
  }

  /**
   * Day of month only ("13"), for narrow chart labels; the full date stays in the bar's tooltip.
   * رقم اليوم فقط ("13") لتسميات الرسم الضيقة؛ والتاريخ الكامل يبقى في تلميح العمود.
   */
  dayOfMonth(isoDate: string): string {
    return String(Number(isoDate.slice(8, 10)));
  }
}
