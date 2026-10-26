import { Routes } from '@angular/router';

import { adminGuard, authGuard, guestGuard } from './core/auth/guards';
import { ShellComponent } from './layout/shell.component';

/**
 * Routes. Feature pages are lazy-loaded (loadComponent), so the login page downloads only what it needs.
 * المسارات. صفحات الميزات تُحمّل عند الحاجة (loadComponent)، فصفحة الدخول تحمّل فقط اللي تحتاجه.
 *
 * Purchase/sales and supplier/customer pages share one component each; route "data" tells them which kind to show.
 * صفحات الشراء/البيع والمورد/العميل تشترك في مكوّن واحد لكل زوج، والـ "data" في المسار تحدد النوع.
 */
export const routes: Routes = [
  {
    path: 'login',
    canActivate: [guestGuard],
    loadComponent: () => import('./features/login/login.page').then((m) => m.LoginPage),
  },
  {
    path: '',
    component: ShellComponent,
    canActivate: [authGuard],
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'dashboard' },
      {
        path: 'dashboard',
        loadComponent: () =>
          import('./features/dashboard/dashboard.page').then((m) => m.DashboardPage),
      },
      {
        path: 'products',
        loadComponent: () =>
          import('./features/products/product-list.page').then((m) => m.ProductListPage),
      },
      {
        path: 'products/new',
        loadComponent: () =>
          import('./features/products/product-form.page').then((m) => m.ProductFormPage),
      },
      {
        path: 'products/:id',
        loadComponent: () =>
          import('./features/products/product-detail.page').then((m) => m.ProductDetailPage),
      },
      {
        path: 'products/:id/edit',
        loadComponent: () =>
          import('./features/products/product-form.page').then((m) => m.ProductFormPage),
      },
      {
        path: 'categories',
        loadComponent: () =>
          import('./features/catalog/categories.page').then((m) => m.CategoriesPage),
      },
      {
        path: 'suppliers',
        data: { kind: 'suppliers' },
        loadComponent: () =>
          import('./features/partners/partners.page').then((m) => m.PartnersPage),
      },
      {
        path: 'customers',
        data: { kind: 'customers' },
        loadComponent: () =>
          import('./features/partners/partners.page').then((m) => m.PartnersPage),
      },
      {
        path: 'purchases',
        data: { kind: 'purchase' },
        loadComponent: () =>
          import('./features/orders/order-list.page').then((m) => m.OrderListPage),
      },
      {
        path: 'purchases/new',
        data: { kind: 'purchase' },
        loadComponent: () =>
          import('./features/orders/order-editor.page').then((m) => m.OrderEditorPage),
      },
      {
        path: 'purchases/:id',
        data: { kind: 'purchase' },
        loadComponent: () =>
          import('./features/orders/order-editor.page').then((m) => m.OrderEditorPage),
      },
      {
        path: 'sales',
        data: { kind: 'sales' },
        loadComponent: () =>
          import('./features/orders/order-list.page').then((m) => m.OrderListPage),
      },
      {
        path: 'sales/new',
        data: { kind: 'sales' },
        loadComponent: () =>
          import('./features/orders/order-editor.page').then((m) => m.OrderEditorPage),
      },
      {
        path: 'sales/:id',
        data: { kind: 'sales' },
        loadComponent: () =>
          import('./features/orders/order-editor.page').then((m) => m.OrderEditorPage),
      },
      {
        path: 'movements',
        loadComponent: () => import('./features/stock/movements.page').then((m) => m.MovementsPage),
      },
      {
        path: 'users',
        canActivate: [adminGuard],
        loadComponent: () => import('./features/users/users.page').then((m) => m.UsersPage),
      },
      {
        path: '**',
        loadComponent: () => import('./features/not-found.page').then((m) => m.NotFoundPage),
      },
    ],
  },
];
