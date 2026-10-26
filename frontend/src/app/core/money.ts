// Must match the server's stockmate.vat-rate (15% in Saudi Arabia), expressed in basis points to stay in integers.
// لازم يطابق stockmate.vat-rate في الخادم (15% في السعودية)، ونكتبه بوحدة الأساس (bp) حتى نبقى في الأعداد الصحيحة.
export const VAT_BASIS_POINTS = 1500;

export interface TotalsLine {
  quantity: number | string;
  unitPrice: number | string;
}

export interface Totals {
  subtotal: number;
  vat: number;
  total: number;
}

/**
 * Converts a money value (e.g. "12.35" or 12.35) to whole halalas (cents). Invalid input counts as 0.
 * يحوّل مبلغ (مثل "12.35" أو 12.35) إلى هللات صحيحة. المدخل غير الصالح يُحسب صفر.
 *
 * Math.round removes float noise such as 12.35 * 100 = 1234.9999999999998.
 * Math.round يشيل تشويش الكسور العشرية مثل 12.35 * 100 = 1234.9999999999998.
 */
export function toCents(value: number | string): number {
  const n = Number(value);
  return Number.isFinite(n) ? Math.round(n * 100) : 0;
}

/**
 * Live totals preview using the same rules as the server, computed in integer halalas so there is no
 * floating-point drift: line = qty × price, subtotal = Σ lines, VAT once on the subtotal rounded half-up.
 * معاينة الإجماليات بنفس قواعد الخادم، محسوبة بالهللات الصحيحة حتى ما يصير انحراف في الكسور العشرية:
 * السطر = الكمية × السعر، المجموع = مجموع الأسطر، والضريبة مرة وحدة على المجموع مع التقريب لأعلى عند النصف.
 *
 * The server recalculates everything on save; this only drives the on-screen preview.
 * الخادم يعيد حساب كل شي عند الحفظ؛ هذا فقط للمعاينة على الشاشة.
 */
export function computeTotals(lines: readonly TotalsLine[]): Totals {
  let subtotalCents = 0;
  for (const line of lines) {
    const qty = Number(line.quantity);
    // Empty or invalid quantities (while the user is still typing) contribute nothing.
    // الكميات الفاضية أو غير الصالحة (أثناء الكتابة) ما تضيف شي.
    if (Number.isFinite(qty) && qty > 0) subtotalCents += Math.trunc(qty) * toCents(line.unitPrice);
  }
  // Integer half-up: (x * bp + 5000) / 10000, floored. Exact while values stay below 2^53.
  // تقريب صحيح لأعلى عند النصف: (x * bp + 5000) / 10000 ثم floor. دقيق ما دامت القيم أقل من 2^53.
  const vatCents = Math.floor((subtotalCents * VAT_BASIS_POINTS + 5000) / 10000);
  return {
    subtotal: subtotalCents / 100,
    vat: vatCents / 100,
    total: (subtotalCents + vatCents) / 100,
  };
}

/**
 * Total of one line, for the per-row column.
 * إجمالي سطر واحد، لعمود كل صف.
 */
export function lineTotal(line: TotalsLine): number {
  return computeTotals([line]).subtotal;
}
