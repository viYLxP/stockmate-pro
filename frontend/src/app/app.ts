import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { RouterOutlet } from '@angular/router';

import { I18nService } from './core/i18n/i18n.service';
import { ConfirmDialogComponent } from './core/ui/confirm-dialog.component';
import { ToastHostComponent } from './core/ui/toast-host.component';

/**
 * Root component: the router outlet plus global overlays (toasts and the confirm dialog).
 * المكوّن الجذري: مكان عرض الصفحات مع العناصر العامة (الإشعارات ونافذة التأكيد).
 */
@Component({
  selector: 'app-root',
  imports: [RouterOutlet, ToastHostComponent, ConfirmDialogComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <router-outlet />
    <app-toast-host />
    <app-confirm-dialog />
  `,
})
export class App {
  // Injecting the service here creates it at startup, so <html lang dir> is set before the first page renders.
  // حقن الخدمة هنا ينشئها عند التشغيل، فيتحدد <html lang dir> قبل ما تظهر أول صفحة.
  protected readonly i18n = inject(I18nService);
}
