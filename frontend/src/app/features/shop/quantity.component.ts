import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';

import { TranslatePipe } from '../../core/i18n/translate.pipe';
import { IconComponent } from '../../core/ui/icon.component';

/**
 * − [n] + quantity control, used by the product page and the cart.
 * عداد الكمية − [n] +، تستخدمه صفحة المنتج والسلة.
 */
@Component({
  selector: 'app-quantity',
  imports: [TranslatePipe, IconComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <span class="qty" role="group" [attr.aria-label]="'shop.quantity' | t">
      <button
        type="button"
        (click)="set(value() - 1)"
        [disabled]="value() <= 1"
        [attr.aria-label]="'shop.decrease' | t"
        data-testid="qty-minus"
      >
        <app-icon name="minus" />
      </button>
      <input
        type="number"
        inputmode="numeric"
        min="1"
        [max]="max()"
        [value]="value()"
        (change)="typed($any($event.target))"
        [attr.aria-label]="'shop.quantity' | t"
        data-testid="qty-input"
      />
      <button
        type="button"
        (click)="set(value() + 1)"
        [disabled]="value() >= max()"
        [attr.aria-label]="'shop.increase' | t"
        data-testid="qty-plus"
      >
        <app-icon name="plus" />
      </button>
    </span>
  `,
})
export class QuantityComponent {
  readonly value = input.required<number>();
  readonly max = input(99);
  readonly valueChange = output<number>();

  // A typed value outside 1..max is corrected in the box too, not only in the cart.
  // القيمة المكتوبة برا 1..max تتصحح في المربع برضو، مو بس في السلة.
  protected typed(box: HTMLInputElement): void {
    box.value = String(this.set(Number(box.value)));
  }

  protected set(next: number): number {
    const clean = Math.min(this.max(), Math.max(1, Math.floor(next) || 1));
    this.valueChange.emit(clean);
    return clean;
  }
}
