import { Routes } from '@angular/router';

import {
  adminGuard,
  customerGuard,
  guestGuard,
  rootRedirect,
  sessionRestore,
  staffGuard,
} from './core/auth/guards';
import { ShellComponent } from './layout/shell.component';
import { ShopLayoutComponent } from './layout/shop-layout.component';

const notFound = () => import('./features/not-found.page').then((m) => m.NotFoundPage);

/**
 * Routes. Feature pages are lazy-loaded (loadComponent), so the login page downloads only what it needs.
 * المسارات. صفحات الميزات تُحمّل عند الحاجة (loadComponent)، فصفحة الدخول تحمّل فقط اللي تحتاجه.
 *
 * Purchase/sales and supplier/customer pages share one component each; route "data" tells them which kind to show.
 * صفحات الشراء/البيع والمورد/العميل تشترك في مكوّن واحد لكل زوج، والـ "data" في المسار تحدد النوع.
 *
 * Two layouts: the shop (visitors and customers) and the office shell (staff). "/" sends each to theirs.
 * تخطيطين: المتجر (الزوار والعملاء) وتطبيق المكتب (الموظفين). و"/" يودّي كل واحد لتخطيطه.
 */
export const routes: Routes = [
  // The guard always redirects, so this component never renders; a route needs one to match.
  // الحارس دايم يحوّل، فهالمكوّن ما ينعرض أبدًا؛ بس المسار يحتاج واحد عشان يتطابق.
  { path: '', pathMatch: 'full', canActivate: [rootRedirect], loadComponent: notFound },
  {
    path: 'login',
    canActivate: [guestGuard],
    loadComponent: () => import('./features/login/login.page').then((m) => m.LoginPage),
  },
  {
    path: 'register',
    canActivate: [guestGuard],
    loadComponent: () => import('./features/login/register.page').then((m) => m.RegisterPage),
  },
  {
    path: '',
    component: ShopLayoutComponent,
    canActivate: [sessionRestore],
    children: [
      {
        path: 'shop',
        loadComponent: () => import('./features/shop/shop.page').then((m) => m.ShopPage),
      },
      {
        path: 'shop/products/:id',
        loadComponent: () => import('./features/shop/product.page').then((m) => m.ShopProductPage),
      },
      {
        path: 'cart',
        loadComponent: () => import('./features/shop/cart.page').then((m) => m.CartPage),
      },
      {
        path: 'checkout',
        loadComponent: () => import('./features/shop/checkout.page').then((m) => m.CheckoutPage),
      },
      {
        path: 'track',
        loadComponent: () => import('./features/shop/track.page').then((m) => m.TrackPage),
      },
      {
        path: 'pay/:id',
        loadComponent: () => import('./features/pay/pay.page').then((m) => m.PayPage),
      },
      {
        path: 'account',
        canActivate: [customerGuard],
        children: [
          {
            path: '',
            loadComponent: () =>
              import('./features/account/account.page').then((m) => m.AccountPage),
          },
          {
            path: 'profile',
            loadComponent: () =>
              import('./features/account/profile.page').then((m) => m.ProfilePage),
          },
          {
            path: 'orders/:number',
            loadComponent: () =>
              import('./features/account/account-order.page').then((m) => m.AccountOrderPage),
          },
        ],
      },
    ],
  },
  {
    path: '',
    component: ShellComponent,
    canActivate: [staffGuard],
    children: [
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
    ],
  },
  // Anything else: the shop's "page not found". / أي شي ثاني: صفحة "غير موجودة" في المتجر.
  {
    path: '',
    component: ShopLayoutComponent,
    canActivate: [sessionRestore],
    children: [{ path: '**', loadComponent: notFound }],
  },
];
