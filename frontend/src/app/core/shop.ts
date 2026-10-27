import { Lang } from './i18n/i18n.service';

// Small shop helpers shared by the storefront, the customer pages and the staff app.
// أدوات صغيرة للمتجر يستخدمها واجهة المتجر وصفحات العميل وتطبيق الموظفين.

// Same rule as the server's Phones.normalize: 05XXXXXXXX, 5XXXXXXXX, +9665…, 009665… with optional spaces/dashes.
// نفس قاعدة Phones.normalize في الخادم: 05XXXXXXXX أو 5XXXXXXXX أو ‎+9665… أو 009665… مع مسافات أو شرطات اختيارية.
// The server still checks it; this only gives instant feedback while typing.
// الخادم يتحقق منه برضو؛ هذا بس يعطي تنبيه فوري وقت الكتابة.
export const SAUDI_MOBILE = /^\s*(?:\+966|00966|966|0)?[\s-]*5(?:[\s-]*\d){8}\s*$/;

// Order numbers look like "SO-000123". / أرقام الطلبات شكلها "SO-000123".
export const ORDER_NUMBER = /^\s*SO-?\d{1,9}\s*$/i;

/** "966512345678" → "0512345678", the way Saudis write and read their mobile numbers. */
/** "966512345678" ← "0512345678"، بالشكل اللي يكتب ويقرأ فيه السعوديون جوالاتهم. */
export function localPhone(phone: string | null | undefined): string {
  if (!phone) return '';
  return phone.startsWith('966') ? `0${phone.slice(3)}` : phone;
}

/** "so-17" → "SO-000017", so a customer can type the number the short way. / حتى العميل يقدر يكتب الرقم مختصر. */
export function normalizeOrderNumber(raw: string): string {
  const digits = raw.trim().toUpperCase().replace(/^SO-?/, '');
  return /^\d+$/.test(digits) ? `SO-${digits.padStart(6, '0')}` : raw.trim().toUpperCase();
}

/**
 * The Arabic text in the Arabic shop when there is one, otherwise the English text.
 * النص العربي في المتجر العربي لو موجود، وإلا النص الإنجليزي.
 */
export function localized(
  lang: Lang,
  en: string | null | undefined,
  ar: string | null | undefined,
): string {
  return (lang === 'ar' && ar ? ar : en) ?? '';
}

// Instalments are offered by the server only between these amounts (INSTALMENTS_MIN / MAX, defaults).
// التقسيط يعرضه الخادم بس بين هالمبلغين (INSTALMENTS_MIN / MAX، القيم الافتراضية).
// Used only to explain on the checkout page why Tabby/Tamara is greyed out; the server decides.
// تُستخدم بس لتوضيح ليش تابي/تمارا مطفية في صفحة الدفع؛ والقرار للخادم.
export const INSTALMENTS_MIN = 100;
export const INSTALMENTS_MAX = 5000;

// A colour per category for the product tiles (the catalogue has no photos). / لون لكل تصنيف لبطاقات المنتجات (الكتالوج بدون صور).
const TILE_HUES = [32, 200, 150, 280, 350, 90, 15, 240];
export function tileHue(categoryId: number): number {
  return TILE_HUES[categoryId % TILE_HUES.length];
}

/**
 * A price with 15% VAT, the way the shop shows prices (same rounding as the server's withVat).
 * The order totals always come from the server; this is only for line labels.
 * السعر مع ضريبة 15%، مثل ما يعرض المتجر الأسعار (نفس تقريب withVat في الخادم).
 * مجاميع الطلب دايم من الخادم؛ وهذا بس لعرض الأسطر.
 */
export function withVat(amount: number): number {
  return Math.round(amount * 115 + 1e-6) / 100;
}
