import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  computed,
  inject,
  input,
  signal,
  viewChild,
} from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';

import { ApiError, errorMessage, toApiError } from '../../core/api/api-error';
import { ApiService } from '../../core/api/api.service';
import { Page, Supplier } from '../../core/api/models';
import { AuthService } from '../../core/auth/auth.service';
import { I18nService } from '../../core/i18n/i18n.service';
import { TranslatePipe } from '../../core/i18n/translate.pipe';
import { ConfirmService } from '../../core/ui/confirm.service';
import { FieldErrorComponent } from '../../core/ui/field-error.component';
import { IconComponent } from '../../core/ui/icon.component';
import { PaginationComponent } from '../../core/ui/pagination.component';
import { ToastService } from '../../core/ui/toast.service';

// Customers have the same fields as suppliers except the VAT number, so one type covers both.
// العملاء لهم نفس حقول الموردين ما عدا الرقم الضريبي، فنوع واحد يكفي للاثنين.
type Partner = Omit<Supplier, 'vatNumber'> & { vatNumber?: string | null };

const PHONE_PATTERN = /^[+0-9][0-9 -]{6,19}$/;
const VAT_PATTERN = /^[0-9]{15}$/;

/**
 * One page for both suppliers and customers. The route's data.kind ("suppliers" | "customers") picks the API
 * endpoint, texts and whether the VAT field is shown.
 * صفحة وحدة للموردين والعملاء. قيمة data.kind في المسار ("suppliers" أو "customers") تحدد رابط الـ API
 * والنصوص وهل يظهر حقل الرقم الضريبي.
 */
@Component({
  selector: 'app-partners',
  imports: [
    ReactiveFormsModule,
    TranslatePipe,
    IconComponent,
    FieldErrorComponent,
    PaginationComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './partners.page.html',
})
export class PartnersPage {
  private readonly api = inject(ApiService);
  private readonly i18n = inject(I18nService);
  private readonly toast = inject(ToastService);
  private readonly confirm = inject(ConfirmService);
  protected readonly auth = inject(AuthService);
  private readonly dialog = viewChild.required<ElementRef<HTMLDialogElement>>('dialog');

  readonly kind = input.required<'suppliers' | 'customers'>();
  protected readonly isSupplier = computed(() => this.kind() === 'suppliers');
  // Translation keys that differ between the two kinds.
  // مفاتيح الترجمة اللي تختلف بين النوعين.
  protected readonly texts = computed(() =>
    this.isSupplier()
      ? {
          title: 'partners.suppliersTitle',
          subtitle: 'partners.suppliersSubtitle',
          add: 'partners.newSupplier',
          edit: 'partners.editSupplier',
        }
      : {
          title: 'partners.customersTitle',
          subtitle: 'partners.customersSubtitle',
          add: 'partners.newCustomer',
          edit: 'partners.editCustomer',
        },
  );

  protected readonly search = signal('');
  protected readonly page = signal(0);
  protected readonly partners = rxResource({
    params: () => ({ kind: this.kind(), q: this.search(), page: this.page(), size: 20 }),
    stream: ({ params: { kind, ...query } }) => this.api.get<Page<Partner>>(`/${kind}`, query),
  });

  protected readonly editing = signal<Partner | null>(null);
  protected readonly saving = signal(false);
  private readonly apiError = signal<ApiError | null>(null);
  protected readonly errorText = computed(() => {
    const err = this.apiError();
    return err && err.code !== 'VALIDATION_FAILED' ? errorMessage(this.i18n, err) : null;
  });
  protected readonly serverFields = computed(() => this.apiError()?.fields ?? {});

  protected readonly form = inject(NonNullableFormBuilder).group({
    name: ['', [Validators.required, Validators.maxLength(150)]],
    phone: ['', Validators.pattern(PHONE_PATTERN)],
    email: ['', [Validators.email, Validators.maxLength(150)]],
    city: ['', Validators.maxLength(80)],
    vatNumber: ['', Validators.pattern(VAT_PATTERN)],
  });

  private searchTimer: ReturnType<typeof setTimeout> | undefined;

  // Debounced search; a new search always starts from the first page.
  // بحث مؤجل؛ وأي بحث جديد يبدأ من الصفحة الأولى.
  protected onSearch(value: string): void {
    clearTimeout(this.searchTimer);
    this.searchTimer = setTimeout(() => {
      this.page.set(0);
      this.search.set(value.trim());
    }, 300);
  }

  protected open(partner: Partner | null): void {
    this.editing.set(partner);
    this.apiError.set(null);
    this.form.reset({
      name: partner?.name ?? '',
      phone: partner?.phone ?? '',
      email: partner?.email ?? '',
      city: partner?.city ?? '',
      vatNumber: partner?.vatNumber ?? '',
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
    const { vatNumber, ...contact } = this.form.getRawValue();
    // Only suppliers have a VAT number, so it is not sent for customers.
    // الرقم الضريبي للموردين فقط، فما نرسله مع العملاء.
    const body = this.isSupplier() ? { ...contact, vatNumber } : contact;
    const editing = this.editing();
    const url = `/${this.kind()}`;
    this.saving.set(true);
    const request = editing
      ? this.api.put<Partner>(`${url}/${editing.id}`, body)
      : this.api.post<Partner>(url, body);
    request.subscribe({
      next: () => {
        this.saving.set(false);
        this.toast.success(this.i18n.t('common.saved'));
        this.close();
        this.partners.reload();
      },
      error: (err) => {
        this.saving.set(false);
        this.apiError.set(toApiError(err));
      },
    });
  }

  protected async remove(partner: Partner): Promise<void> {
    const ok = await this.confirm.ask({
      title: this.i18n.t('common.confirmTitle'),
      message: this.i18n.t('common.deleteConfirm', { name: partner.name }),
      confirmLabel: this.i18n.t('common.delete'),
      danger: true,
    });
    if (!ok) return;
    this.api.delete(`/${this.kind()}/${partner.id}`).subscribe({
      next: () => {
        this.toast.success(this.i18n.t('common.deleted'));
        this.partners.reload();
      },
      error: (err) => this.toast.error(errorMessage(this.i18n, toApiError(err))),
    });
  }
}
