import { computeTotals, lineTotal, toCents } from './money';

// The preview must match what the server stores, otherwise the user sees one total and gets another.
// المعاينة لازم تطابق اللي يحفظه الخادم، وإلا المستخدم يشوف إجمالي ويطلع له غيره.
describe('money', () => {
  it('converts prices to whole halalas without float noise', () => {
    expect(toCents('12.35')).toBe(1235);
    expect(toCents(0.1 + 0.2)).toBe(30);
    expect(toCents('')).toBe(0);
    expect(toCents('abc')).toBe(0);
  });

  it('computes 15% VAT once on the subtotal', () => {
    // 3 × 0.10 = 0.30 → VAT 0.045 → 0.05 (half-up), total 0.35.
    // 3 × 0.10 = 0.30 ← الضريبة 0.045 ← 0.05 (تقريب لأعلى عند النصف)، والإجمالي 0.35.
    expect(computeTotals([{ quantity: 3, unitPrice: '0.10' }])).toEqual({
      subtotal: 0.3,
      vat: 0.05,
      total: 0.35,
    });
  });

  it('does not round VAT per line', () => {
    // Per-line VAT would give 0.02 + 0.02 = 0.04; on the subtotal (0.30) it is 0.045 → 0.05.
    // الضريبة لكل سطر كانت بتعطي 0.02 + 0.02 = 0.04؛ وعلى المجموع (0.30) هي 0.045 ← 0.05.
    const totals = computeTotals([
      { quantity: 1, unitPrice: '0.15' },
      { quantity: 1, unitPrice: '0.15' },
    ]);
    expect(totals.vat).toBe(0.05);
    expect(totals.total).toBe(0.35);
  });

  it('stays exact for large orders', () => {
    const totals = computeTotals([{ quantity: 1_000_000, unitPrice: '9999999.99' }]);
    expect(totals.subtotal).toBe(9_999_999_990_000);
    expect(totals.vat).toBe(1_499_999_998_500);
  });

  it('ignores lines that are still being typed', () => {
    expect(
      computeTotals([
        { quantity: '', unitPrice: '5' },
        { quantity: 2, unitPrice: '' },
      ]),
    ).toEqual({ subtotal: 0, vat: 0, total: 0 });
    expect(computeTotals([])).toEqual({ subtotal: 0, vat: 0, total: 0 });
    expect(lineTotal({ quantity: 4, unitPrice: '2.50' })).toBe(10);
  });
});
