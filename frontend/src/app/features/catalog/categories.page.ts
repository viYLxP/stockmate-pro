import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  computed,
  inject,
  signal,
  viewChild,
} from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';

import { ApiError, errorMessage, toApiError } from '../../core/api/api-error';
import { ApiService } from '../../core/api/api.service';
import { Category } from '../../core/api/models';
import { AuthService } from '../../core/auth/auth.service';
import { I18nService } from '../../core/i18n/i18n.service';
import { TranslatePipe } from '../../core/i18n/translate.pipe';
import { ConfirmService } from '../../core/ui/confirm.service';
import { FieldErrorComponent } from '../../core/ui/field-error.component';
import { IconComponent } from '../../core/ui/icon.component';
import { ToastService } from '../../core/ui/toast.service';

/**
 * Category list with a create/edit dialog. Categories are few, so the whole list loads at once (no pagination).
 * قائمة التصنيفات مع نافذة إنشاء/تعديل. التصنيفات قليلة، فتتحمل القائمة كاملة مرة وحدة (بدون صفحات).
 */
@Component({
  selector: 'app-categories',
  imports: [ReactiveFormsModule, RouterLink, TranslatePipe, IconComponent, FieldErrorComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './categories.page.html',
})
export class CategoriesPage {
  private readonly api = inject(ApiService);
  private readonly i18n = inject(I18nService);
  private readonly toast = inject(ToastService);
  private readonly confirm = inject(ConfirmService);
  protected readonly auth = inject(AuthService);
  private readonly dialog = viewChild.required<ElementRef<HTMLDialogElement>>('dialog');

  protected readonly categories = rxResource({
    stream: () => this.api.get<Category[]>('/categories'),
  });
  // null = creating a new category, otherwise the category being edited.
  // null = إنشاء تصنيف جديد، وإلا هو التصنيف اللي نعدّله.
  protected readonly editing = signal<Category | null>(null);
  protected readonly saving = signal(false);
  private readonly apiError = signal<ApiError | null>(null);
  protected readonly errorText = computed(() => {
    const err = this.apiError();
    return err && err.code !== 'VALIDATION_FAILED' ? errorMessage(this.i18n, err) : null;
  });
  protected readonly serverFields = computed(() => this.apiError()?.fields ?? {});

  protected readonly form = inject(NonNullableFormBuilder).group({
    name: ['', [Validators.required, Validators.maxLength(100)]],
    // Shown in the Arabic shop. / يظهر في المتجر العربي.
    nameAr: ['', Validators.maxLength(100)],
    description: ['', Validators.maxLength(255)],
  });

  protected open(category: Category | null): void {
    this.editing.set(category);
    this.apiError.set(null);
    this.form.reset({
      name: category?.name ?? '',
      nameAr: category?.nameAr ?? '',
      description: category?.description ?? '',
    });
    this.dialog().nativeElement.showModal();
  }

  protected close(): void {
    this.dialog().nativeElement.close();
  }

  protected submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const editing = this.editing();
    const v = this.form.getRawValue();
    const body = { ...v, nameAr: v.nameAr.trim() || null };
    this.saving.set(true);
    const request = editing
      ? this.api.put<Category>(`/categories/${editing.id}`, body)
      : this.api.post<Category>('/categories', body);
    request.subscribe({
      next: () => {
        this.saving.set(false);
        this.toast.success(this.i18n.t('common.saved'));
        this.close();
        this.categories.reload();
      },
      error: (err) => {
        this.saving.set(false);
        this.apiError.set(toApiError(err));
      },
    });
  }

  protected async remove(category: Category): Promise<void> {
    const ok = await this.confirm.ask({
      title: this.i18n.t('common.confirmTitle'),
      message: this.i18n.t('common.deleteConfirm', { name: category.name }),
      confirmLabel: this.i18n.t('common.delete'),
      danger: true,
    });
    if (!ok) return;
    this.api.delete(`/categories/${category.id}`).subscribe({
      next: () => {
        this.toast.success(this.i18n.t('common.deleted'));
        this.categories.reload();
      },
      error: (err) => this.toast.error(errorMessage(this.i18n, toApiError(err))),
    });
  }
}
