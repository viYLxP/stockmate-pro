// TypeScript mirrors of the backend DTOs. Keeping them in one file makes API changes easy to spot.
// نسخ TypeScript من الـ DTOs في الخادم. وجودها في ملف واحد يسهّل ملاحظة أي تغيير في الـ API.
// Money values arrive as JSON numbers (BigDecimal on the server, 2 decimals).
// المبالغ تصل كأرقام JSON (BigDecimal في الخادم بمنزلتين).

export type Role = 'ADMIN' | 'STAFF' | 'CUSTOMER';
export type StockStatus = 'OK' | 'LOW' | 'OUT';
export type PurchaseStatus = 'DRAFT' | 'RECEIVED' | 'CANCELLED';
export type SalesStatus = 'DRAFT' | 'CONFIRMED' | 'CANCELLED';
export type OrderStatus = PurchaseStatus | SalesStatus;
export type SalesChannel = 'OFFICE' | 'ONLINE';
export type Fulfilment = 'NEW' | 'SHIPPED' | 'DELIVERED';
export type City = 'RIYADH' | 'JEDDAH' | 'DAMMAM' | 'KHOBAR' | 'MAKKAH' | 'MADINAH';
export const CITIES: City[] = ['RIYADH', 'JEDDAH', 'DAMMAM', 'KHOBAR', 'MAKKAH', 'MADINAH'];
export type PaymentStatus = 'UNPAID' | 'PAID' | 'REFUNDED';
export type PaymentMethod = 'CARD' | 'APPLE_PAY' | 'TABBY' | 'TAMARA' | 'CASH';
export type PaymentProvider = 'SANDBOX' | 'MOYASAR' | 'STRIPE' | 'TABBY' | 'TAMARA' | 'CASH';
export type PaymentAttemptStatus = 'PENDING' | 'PAID' | 'FAILED' | 'REFUNDED';
export type MovementType =
  'OPENING' | 'PURCHASE' | 'SALE' | 'SALE_RETURN' | 'ADJUSTMENT_IN' | 'ADJUSTMENT_OUT';

export interface Page<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface User {
  id: number;
  username: string;
  fullName: string;
  role: Role;
  // Set for shop customers (their login is the phone, stored as 9665XXXXXXXX).
  // موجود لعملاء المتجر (دخولهم بالجوال، محفوظ بالشكل 9665XXXXXXXX).
  phone: string | null;
  email: string | null;
  active: boolean;
  createdAt: string;
}

export interface LoginResponse {
  token: string;
  expiresAt: string;
  user: User;
}

export interface Category {
  id: number;
  name: string;
  nameAr: string | null;
  description: string | null;
  productCount: number;
}

export interface Supplier {
  id: number;
  name: string;
  phone: string | null;
  email: string | null;
  city: string | null;
  vatNumber: string | null;
}

export interface Customer {
  id: number;
  name: string;
  phone: string | null;
  email: string | null;
  city: string | null;
}

export interface Product {
  id: number;
  sku: string;
  name: string;
  categoryId: number;
  categoryName: string;
  supplierId: number | null;
  supplierName: string | null;
  unit: string;
  costPrice: number;
  salePrice: number;
  quantity: number;
  reorderLevel: number;
  stockStatus: StockStatus;
  stockValue: number;
  active: boolean;
  version: number;
  updatedAt: string;
  shop: ShopListing;
}

/** How a product appears in the online shop. / كيف يظهر المنتج في المتجر الإلكتروني. */
export interface ShopListing {
  published: boolean;
  nameAr: string | null;
  description: string | null;
  descriptionAr: string | null;
}

export interface Movement {
  id: number;
  productId: number;
  productSku: string;
  productName: string;
  type: MovementType;
  quantity: number;
  balanceAfter: number;
  reference: string | null;
  note: string | null;
  createdBy: string;
  createdAt: string;
}

export interface OrderLine {
  productId: number;
  productSku: string;
  productName: string;
  unit: string;
  quantity: number;
  // Purchase lines carry unitCost, sales lines carry unitPrice; the UI reads whichever exists.
  // أسطر الشراء فيها unitCost وأسطر البيع فيها unitPrice؛ والواجهة تقرأ الموجود منهما.
  unitCost?: number;
  unitPrice?: number;
  lineTotal: number;
  availableQuantity?: number;
}

/** Common shape of purchase and sales orders. / الشكل المشترك لطلبات الشراء والبيع. */
export interface Order {
  id: number;
  number: string;
  supplierId?: number;
  supplierName?: string;
  customerId?: number;
  customerName?: string;
  status: OrderStatus;
  notes: string | null;
  subtotal: number;
  vat: number;
  total: number;
  createdBy: string;
  createdAt: string;
  receivedAt?: string | null;
  confirmedAt?: string | null;
  lines: OrderLine[];
  // Sales orders only; the shop fields are null for office orders. / لطلبات البيع بس؛ وحقول المتجر null لطلبات المكتب.
  channel?: SalesChannel;
  paymentStatus?: PaymentStatus;
  paymentChoice?: PaymentMethod | null;
  fulfilment?: Fulfilment | null;
  shipTo?: ShipTo | null;
  payBy?: string | null;
  shippedAt?: string | null;
  deliveredAt?: string | null;
  cancelledAt?: string | null;
  cancelReason?: string | null;
  payments?: PaymentView[];
}

export interface ShipTo {
  name: string;
  phone: string;
  email: string | null;
  city: City;
  district: string;
  address: string;
}

export interface OrderSummary {
  id: number;
  number: string;
  supplierId?: number;
  supplierName?: string;
  customerId?: number;
  customerName?: string;
  status: OrderStatus;
  total: number;
  createdAt: string;
  receivedAt?: string | null;
  confirmedAt?: string | null;
  channel?: SalesChannel;
  paymentStatus?: PaymentStatus;
  paymentChoice?: PaymentMethod | null;
  fulfilment?: Fulfilment | null;
}

export interface LineRequest {
  productId: number;
  quantity: number;
  unitPrice: number | null;
}

export interface Dashboard {
  activeProducts: number;
  lowStockCount: number;
  outOfStockCount: number;
  inventoryCostValue: number;
  inventoryRetailValue: number;
  salesThisMonth: number;
  grossProfitThisMonth: number;
  purchasesThisMonth: number;
  draftSalesOrders: number;
  draftPurchaseOrders: number;
  dailySales: { date: string; total: number; orders: number }[];
  topProducts: {
    productId: number;
    sku: string;
    name: string;
    quantity: number;
    revenue: number;
  }[];
  reorderList: Product[];
  recentMovements: Movement[];
  shop: { toShip: number; outForDelivery: number; collectedThisMonth: number };
}

// ----- Payments / المدفوعات -----

/** One payment attempt as the office sees it. / محاولة دفع وحدة مثل ما يشوفها المكتب. */
export interface PaymentView {
  id: string;
  method: PaymentMethod;
  provider: PaymentProvider;
  status: PaymentAttemptStatus;
  amount: number;
  currency: string;
  cardBrand: string | null;
  cardLast4: string | null;
  failureReason: string | null;
  recordedBy: string;
  createdAt: string;
  paidAt: string | null;
  refundedAt: string | null;
}

export interface Instalment {
  number: number;
  amount: number;
  dueDate: string;
}

/** The payment part of the customer's view of an order. / جزء الدفع في عرض العميل للطلب. */
export interface PaymentSummary {
  status: PaymentStatus;
  payable: boolean;
  amountDue: number | null;
  // Online methods offered right now (instalments only within their limits).
  // الطرق المتاحة الحين أونلاين (التقسيط بس داخل حدوده).
  methods: PaymentMethod[];
  instalments: Instalment[];
  method: PaymentMethod | null;
  cardBrand: string | null;
  cardLast4: string | null;
  paidAt: string | null;
}

export interface CheckoutResponse {
  paymentId: string;
  checkoutUrl: string;
}

/** What the /pay/:id page shows. / اللي تعرضه صفحة ‎/pay/:id. */
export interface CheckoutPage {
  id: string;
  method: PaymentMethod;
  status: PaymentAttemptStatus;
  provider: PaymentProvider;
  amount: number;
  currency: string;
  orderNumber: string;
  itemCount: number;
  instalments: Instalment[];
  failureReason: string | null;
  cardBrand: string | null;
  cardLast4: string | null;
  paidAt: string | null;
  checkoutUrl: string | null;
}

// ----- Online shop / المتجر الإلكتروني -----

/** A product as shoppers see it; prices include VAT. / المنتج مثل ما يشوفه المتسوق؛ الأسعار شاملة الضريبة. */
export interface ShopProduct {
  id: number;
  sku: string;
  name: string;
  nameAr: string | null;
  description: string | null;
  descriptionAr: string | null;
  categoryId: number;
  categoryName: string;
  categoryNameAr: string | null;
  unit: string;
  price: number;
  inStock: boolean;
  // Only when few are left. / بس لو باقي قليل.
  left: number | null;
}

export interface ShopCategory {
  id: number;
  name: string;
  nameAr: string | null;
  productCount: number;
}

export interface CartItem {
  productId: number;
  quantity: number;
}

export interface QuoteLine {
  productId: number;
  name: string | null;
  nameAr: string | null;
  quantity: number;
  unitPrice: number | null;
  lineTotal: number | null;
  // UNAVAILABLE (not sold any more) or STOCK (fewer left than asked). / ما عاد ينباع، أو الباقي أقل من المطلوب.
  problem: 'UNAVAILABLE' | 'STOCK' | null;
  available: number | null;
}

export interface Quote {
  lines: QuoteLine[];
  subtotal: number;
  vat: number;
  total: number;
  canCheckout: boolean;
}

export interface CheckoutResult {
  number: string;
  total: number;
  paymentMethod: PaymentMethod;
  paymentId: string | null;
  checkoutUrl: string | null;
  payBy: string | null;
}

export interface ShopOrderLine {
  productId: number;
  sku: string;
  name: string;
  nameAr: string | null;
  quantity: number;
  unitPrice: number;
  lineTotal: number;
}

/** A shop order as its customer sees it. / طلب المتجر مثل ما يشوفه صاحبه. */
export interface ShopOrder {
  number: string;
  status: SalesStatus;
  fulfilment: Fulfilment | null;
  createdAt: string;
  payBy: string | null;
  shippedAt: string | null;
  deliveredAt: string | null;
  cancelledAt: string | null;
  cancelReason: string | null;
  lines: ShopOrderLine[];
  subtotal: number;
  vat: number;
  total: number;
  paymentChoice: PaymentMethod;
  payment: PaymentSummary;
  shipTo: ShipTo;
  cancellable: boolean;
}

/** One row of "My orders". / سطر في "طلباتي". */
export interface ShopOrderRow {
  number: string;
  status: SalesStatus;
  fulfilment: Fulfilment | null;
  createdAt: string;
  itemCount: number;
  total: number;
  paymentChoice: PaymentMethod;
  paymentStatus: PaymentStatus;
  payable: boolean;
}
