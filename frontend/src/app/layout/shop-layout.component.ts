import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { filter } from 'rxjs';

import { AuthService } from '../core/auth/auth.service';
import { CartService } from '../core/cart.service';
import { I18nService } from '../core/i18n/i18n.service';
import { TranslatePipe } from '../core/i18n/translate.pipe';
import { IconComponent } from '../core/ui/icon.component';

/**
 * The storefront layout for visitors and customers: header with the cart and language switch, the page, the footer.
 * تخطيط المتجر للزوار والعملاء: رأس فيه السلة وتبديل اللغة، ثم الصفحة، ثم التذييل.
 */
@Component({
  selector: 'app-shop-layout',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, TranslatePipe, IconComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <a class="skip-link" href="#main">{{ 'app.skipToContent' | t }}</a>
    <header class="shop-header">
      <div class="shop-header__inner">
        <a class="brand" routerLink="/shop" data-testid="brand">
          <span class="logo" aria-hidden="true">SM</span>
          <span class="brand__text">
            <strong>{{ 'shop.name' | t }}</strong>
            <small>{{ 'shop.tagline' | t }}</small>
          </span>
        </a>
        <!-- Outside the collapsible menu, so switching language is one tap on phones too. -->
        <!-- خارج القائمة القابلة للطي، حتى تبديل اللغة يكون بضغطة وحدة في الجوال برضو. -->
        <button
          type="button"
          class="btn btn--small lang-switch"
          (click)="i18n.toggle()"
          [attr.aria-label]="'app.switchToLabel' | t"
          [attr.lang]="i18n.lang() === 'ar' ? 'en' : 'ar'"
          data-testid="lang-switch"
        >
          <app-icon name="globe" /> {{ 'app.switchTo' | t }}
        </button>
        <button
          type="button"
          class="icon-btn shop-header__toggle"
          (click)="menuOpen.set(!menuOpen())"
          [attr.aria-label]="(menuOpen() ? 'nav.closeMenu' : 'nav.menu') | t"
          aria-controls="shop-nav"
          [attr.aria-expanded]="menuOpen()"
        >
          <app-icon [name]="menuOpen() ? 'x' : 'menu'" />
        </button>
        <nav
          id="shop-nav"
          class="shop-nav"
          [class.shop-nav--open]="menuOpen()"
          [attr.aria-label]="'shop.nav' | t"
        >
          <a
            routerLink="/shop"
            routerLinkActive="is-active"
            [routerLinkActiveOptions]="{ exact: true }"
            ariaCurrentWhenActive="page"
            ><app-icon name="store" /> {{ 'shop.home' | t }}</a
          >
          <a routerLink="/track" routerLinkActive="is-active" ariaCurrentWhenActive="page"
            ><app-icon name="truck" /> {{ 'shop.track' | t }}</a
          >
          <!-- A guest signs in or signs up; a customer gets their account; staff get back to the office. -->
          <!-- الزائر يدخل أو يسجّل؛ والعميل يروح لحسابه؛ والموظف يرجع للمكتب. -->
          @if (auth.isCustomer()) {
            <a
              routerLink="/account"
              routerLinkActive="is-active"
              ariaCurrentWhenActive="page"
              data-testid="nav-account"
              ><app-icon name="user" /> {{ 'shop.account' | t }}</a
            >
          } @else if (auth.user()) {
            <a routerLink="/dashboard"><app-icon name="dashboard" /> {{ 'nav.dashboard' | t }}</a>
          } @else {
            <a routerLink="/login" data-testid="nav-login"
              ><app-icon name="user" /> {{ 'shop.signIn' | t }}</a
            >
            <a routerLink="/register" data-testid="nav-register">{{ 'shop.register' | t }}</a>
          }
        </nav>
        <!-- The cart stays in the bar on phones too, outside the collapsible menu. -->
        <!-- السلة تبقى في الشريط حتى في الجوال، برا القائمة القابلة للطي. -->
        <a
          class="btn btn--primary cart-link"
          routerLink="/cart"
          [attr.aria-label]="'shop.cartCount' | t: { count: cart.count() }"
          data-testid="nav-cart"
        >
          <app-icon name="cart" /> <span class="cart-link__text">{{ 'shop.cart' | t }}</span>
          @if (cart.count()) {
            <span class="cart-link__count" data-testid="cart-count">{{ cart.count() }}</span>
          }
        </a>
      </div>
    </header>

    <main id="main" class="shop-main" tabindex="-1">
      <router-outlet />
    </main>

    <footer class="shop-footer">
      <div class="shop-footer__inner">
        <div>
          <strong>{{ 'shop.name' | t }}</strong>
          <p>{{ 'shop.featurePay' | t }} · {{ 'shop.featureCash' | t }}</p>
          <p>{{ 'shop.footer' | t }}</p>
        </div>
        <nav [attr.aria-label]="'shop.nav' | t" class="shop-footer__links">
          <a routerLink="/shop">{{ 'shop.home' | t }}</a>
          <a routerLink="/track">{{ 'shop.track' | t }}</a>
          <a routerLink="/login" data-testid="footer-staff">{{ 'shop.staffLink' | t }}</a>
        </nav>
      </div>
    </footer>
  `,
})
export class ShopLayoutComponent {
  protected readonly i18n = inject(I18nService);
  protected readonly auth = inject(AuthService);
  protected readonly cart = inject(CartService);
  protected readonly menuOpen = signal(false);

  constructor() {
    // Close the mobile menu after each navigation. / نقفل قائمة الجوال بعد كل تنقل.
    inject(Router)
      .events.pipe(
        filter((e) => e instanceof NavigationEnd),
        takeUntilDestroyed(),
      )
      .subscribe(() => this.menuOpen.set(false));
  }
}
