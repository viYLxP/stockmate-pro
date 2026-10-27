import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';

import { errorMessage, toApiError } from '../../core/api/api-error';
import { ApiService } from '../../core/api/api.service';
import { ShopProduct } from '../../core/api/models';
import { CartService, MAX_QUANTITY } from '../../core/cart.service';
import { FormatService } from '../../core/i18n/format.service';
import { I18nService } from '../../core/i18n/i18n.service';
import { TranslatePipe } from '../../core/i18n/translate.pipe';
import { localized, tileHue } from '../../core/shop';
import { IconComponent } from '../../core/ui/icon.component';
import { ToastService } from '../../core/ui/toast.service';
import { QuantityComponent } from './quantity.component';

/**
 * One product in the shop (/shop/products/:id): description, price with VAT, stock hint and add to cart.
 * منتج واحد في المتجر: الوصف، والسعر شامل الضريبة، وتلميح المخزون، والإضافة للسلة.
 */
@Component({
  selector: 'app-shop-product',
  imports: [RouterLink, TranslatePipe, IconComponent, QuantityComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="shop-page">
      <p class="section-gap">
        <a routerLink="/shop" class="btn btn--small"
          ><app-icon name="chevron" [flip]="true" class="icon--back" /> {{ 'shop.back' | t }}</a
        >
      </p>
      @if (product.error()) {
        <div class="empty-state empty-state--page" data-testid="product-missing">
          <p>{{ notFoundText() }}</p>
          <a class="btn btn--primary" routerLink="/shop">{{ 'shop.back' | t }}</a>
        </div>
      } @else if (product.value(); as p) {
        <article class="product-detail" data-testid="product-detail">
          <span class="product-tile product-tile--large" [style.--hue]="hue(p.categoryId)">
            <span class="product-tile__inner"
              ><app-icon name="package" /><span dir="ltr">{{ p.sku }}</span></span
            >
          </span>
          <div>
            <a class="list__sub" routerLink="/shop" [queryParams]="{ category: p.categoryId }">{{
              name(p.categoryName, p.categoryNameAr)
            }}</a>
            <h1>{{ name(p.name, p.nameAr) }}</h1>
            <p class="price">
              {{ fmt.money(p.price) }}
              <small
                >{{ 'shop.vatIncluded' | t }} · {{ 'shop.perUnit' | t: { unit: p.unit } }}</small
              >
            </p>
            @if (!p.inStock) {
              <p class="stock-hint stock-hint--out">{{ 'shop.outOfStock' | t }}</p>
            } @else if (p.left) {
              <p class="stock-hint stock-hint--low">
                {{ 'shop.left' | t: { count: fmt.number(p.left) } }}
              </p>
            } @else {
              <p class="stock-hint">{{ 'shop.inStock' | t }}</p>
            }
            @if (description(p); as text) {
              <p class="product-detail__desc" dir="auto">{{ text }}</p>
            }
            <p class="muted mono" dir="ltr">{{ 'shop.sku' | t: { sku: p.sku } }}</p>
            @if (p.inStock) {
              <div class="buy-row">
                <app-quantity
                  [value]="quantity()"
                  [max]="p.left ?? maxQuantity"
                  (valueChange)="quantity.set($event)"
                />
                <button
                  type="button"
                  class="btn btn--primary"
                  (click)="add(p)"
                  data-testid="add-to-cart"
                >
                  <app-icon name="cart" /> {{ 'shop.add' | t }}
                </button>
                @if (cart.count()) {
                  <a class="btn" routerLink="/cart">{{ 'shop.viewCart' | t }}</a>
                }
              </div>
            }
          </div>
        </article>
      } @else {
        <div class="loading" role="status">
          <span class="spinner"></span>{{ 'app.loading' | t }}
        </div>
      }
    </div>
  `,
})
export class ShopProductPage {
  private readonly api = inject(ApiService);
  private readonly toast = inject(ToastService);
  private readonly i18n = inject(I18nService);
  protected readonly fmt = inject(FormatService);
  protected readonly cart = inject(CartService);

  readonly id = input.required<string>();

  protected readonly maxQuantity = MAX_QUANTITY;
  protected readonly quantity = signal(1);
  protected readonly product = rxResource({
    params: () => this.id(),
    stream: ({ params }) => this.api.get<ShopProduct>(`/shop/products/${params}`),
  });
  protected readonly notFoundText = computed(() => {
    const err = toApiError(this.product.error());
    return err.code === 'NOT_FOUND' || err.status === 400
      ? this.i18n.t('shop.notFound')
      : errorMessage(this.i18n, err);
  });

  protected name(en: string, ar: string | null): string {
    return localized(this.i18n.lang(), en, ar);
  }

  protected description(p: ShopProduct): string {
    return localized(this.i18n.lang(), p.description, p.descriptionAr);
  }

  protected hue(categoryId: number): number {
    return tileHue(categoryId);
  }

  protected add(p: ShopProduct): void {
    if (this.cart.add(p, this.quantity())) {
      this.toast.success(this.i18n.t('shop.added'));
      this.quantity.set(1);
    } else {
      this.toast.error(this.i18n.t('shop.cartFull'));
    }
  }
}
