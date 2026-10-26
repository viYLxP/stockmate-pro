import { dayStartIso } from './movements.page';

describe('dayStartIso', () => {
  it('returns local midnight of the chosen day', () => {
    const iso = dayStartIso('2026-10-26')!;
    const d = new Date(iso);
    expect([d.getFullYear(), d.getMonth(), d.getDate(), d.getHours(), d.getMinutes()]).toEqual([
      2026, 9, 26, 0, 0,
    ]);
  });

  it('moves to the next day for the exclusive "to" bound, across month ends', () => {
    const d = new Date(dayStartIso('2026-10-31', 1)!);
    expect([d.getMonth(), d.getDate()]).toEqual([10, 1]);
  });

  it('rejects malformed input', () => {
    expect(dayStartIso('')).toBeNull();
    expect(dayStartIso('26/10/2026')).toBeNull();
  });
});
