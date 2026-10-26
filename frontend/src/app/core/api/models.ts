// TypeScript mirrors of the backend DTOs. Keeping them in one file makes API changes easy to spot.
// نسخ TypeScript من الـ DTOs في الخادم. وجودها في ملف واحد يسهّل ملاحظة أي تغيير في الـ API.
// Money values arrive as JSON numbers (BigDecimal on the server, 2 decimals).
// المبالغ تصل كأرقام JSON (BigDecimal في الخادم بمنزلتين).

export type Role = 'ADMIN' | 'STAFF';
export type StockStatus = 'OK' | 'LOW' | 'OUT';
export type PurchaseStatus = 'DRAFT' | 'RECEIVED' | 'CANCELLED';
export type SalesStatus = 'DRAFT' | 'CONFIRMED' | 'CANCELLED';
export type OrderStatus = PurchaseStatus | SalesStatus;
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
}
