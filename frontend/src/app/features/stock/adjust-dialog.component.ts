import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  computed,
  inject,
  output,
  signal,
  viewChild,
} from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';

import { ApiError, errorMessage, toApiError } from '../../core/api/api-error';
import { ApiService } from '../../core/api/api.service';
import { Movement } from '../../core/api/models';
import { I18nService } from '../../core/i18n/i18n.service';
import { TranslatePipe } from '../../core/i18n/translate.pipe';
import { FieldErrorComponent } from '../../core/ui/field-error.component';
import { ToastService } from '../../core/ui/toast.service';

export interface AdjustTarget {
  id: number;
  name: string;
  sku: string;
  quantity: number;
}

/**
 * Manual stock adjustment dialog (after a physical count, damage, loss...).
 * نافذة تسوية المخزون اليدوية (بعد الجرد أو التلف أو الفقد...).
 *
 * Usage: call open(product) from the parent; the (adjusted) output fires after a successful save.
 * الاستخدام: الأب يستدعي open(product)، والحدث (adjusted) ينطلق بعد الحفظ الناجح.
 */
@Component({
  selector: 'app-adjust-dialog',
  imports: [ReactiveFormsModule, TranslatePipe, FieldErrorComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <dialog #dialog class="dialog" aria-labelledby="adjust-title" (close)="target.set(null)">
      @if (target(); as p) {
        <form [formGroup]="form" (ngSubmit)="submit(p)" novalidate>
          <h2 id="adjust-title">{{ 'movements.newAdjustment' | t }}</h2>
          <p class="muted">
            <bdi>{{ p.name }}</bdi> · <span class="sku" dir="ltr">{{ p.sku }}</span> ·
            {{ 'movements.currentQty' | t: { qty: p.quantity } }}
          </p>
          @if (errorText()) {
            <div class="alert alert--danger" role="alert">{{ errorText() }}</div>
          }

          <fieldset class="field" style="border:0;padding:0;margin:0 0 1rem">
            <legend class="field__label">{{ 'movements.direction' | t }}</legend>
            <label class="checkbox"
              ><input type="radio" formControlName="direction" value="IN" />
              {{ 'movements.in' | t }}</label
            >
            <label class="checkbox"
              ><input type="radio" formControlName="direction" value="OUT" />
              {{ 'movements.out' | t }}</label
            >
          </fieldset>
          <div class="form-grid">
            <div class="field">
              <label for="adj-qty">{{ 'products.quantity' | t }}</label>
              <input
                id="adj-qty"
                type="number"
                min="1"
                step="1"
                inputmode="numeric"
                formControlName="quantity"
                aria-describedby="adj-qty-err"
              />
              <app-field-error [control]="form.controls.quantity" id="adj-qty-err" />
            </div>
            <div class="field form-grid--full">
              <label for="adj-note">{{ 'movements.reason' | t }}</label>
              <input
                id="adj-note"
                formControlName="note"
                maxlength="255"
                aria-describedby="adj-note-hint adj-note-err"
              />
              <p class="hint" id="adj-note-hint">{{ 'movements.reasonHint' | t }}</p>
              <app-field-error [control]="form.controls.note" id="adj-note-err" />
            </div>
          </div>
          <div class="dialog__actions">
            <button type="button" class="btn" (click)="close()">{{ 'common.cancel' | t }}</button>
            <button
              type="submit"
              class="btn btn--primary"
              [disabled]="saving()"
              data-testid="adjust-save"
            >
              {{ (saving() ? 'common.saving' : 'common.save') | t }}
            </button>
          </div>
        </form>
      }
    </dialog>
  `,
})
export class AdjustDialogComponent {
  private readonly api = inject(ApiService);
  private readonly i18n = inject(I18nService);
  private readonly toast = inject(ToastService);
  private readonly dialog = viewChild.required<ElementRef<HTMLDialogElement>>('dialog');

  readonly adjusted = output<Movement>();
  protected readonly target = signal<AdjustTarget | null>(null);
  protected readonly saving = signal(false);
  private readonly apiError = signal<ApiError | null>(null);
  protected readonly errorText = computed(() => {
    const err = this.apiError();
    return err ? errorMessage(this.i18n, err) : null;
  });

  protected readonly form = inject(NonNullableFormBuilder).group({
    direction: ['IN' as 'IN' | 'OUT', Validators.required],
    quantity: [1, [Validators.required, Validators.min(1), Validators.max(1_000_000)]],
    // A whitespace-only reason is rejected too (the server trims and checks NotBlank).
    // السبب المكوّن من مسافات فقط مرفوض أيضًا (الخادم يقص المسافات ويتحقق NotBlank).
    note: ['', [Validators.required, Validators.pattern(/\S/), Validators.maxLength(255)]],
  });

  open(product: AdjustTarget): void {
    this.form.reset({ direction: 'IN', quantity: 1, note: '' });
    this.apiError.set(null);
    this.target.set(product);
    // Wait one tick so the @if content exists before showing the modal.
    // ننتظر لحظة حتى يكون محتوى @if موجود قبل نعرض النافذة.
    queueMicrotask(() => this.dialog().nativeElement.showModal());
  }

  protected close(): void {
    this.dialog().nativeElement.close();
  }

  protected submit(product: AdjustTarget): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const v = this.form.getRawValue();
    this.saving.set(true);
    this.api
      .post<Movement>('/stock/adjustments', {
        productId: product.id,
        direction: v.direction,
        quantity: Number(v.quantity),
        note: v.note.trim(),
      })
      .subscribe({
        next: (movement) => {
          this.saving.set(false);
          this.toast.success(this.i18n.t('movements.adjusted'));
          this.close();
          this.adjusted.emit(movement);
        },
        error: (err) => {
          this.saving.set(false);
          this.apiError.set(toApiError(err));
        },
      });
  }
}
