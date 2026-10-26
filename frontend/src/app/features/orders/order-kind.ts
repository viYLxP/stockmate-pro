// Differences between purchase and sales orders, in one place, so the list and editor pages can be shared.
// الفروقات بين طلبات الشراء والبيع في مكان واحد، حتى تشترك صفحات القائمة والمحرر بين النوعين.
export type OrderKind = 'purchase' | 'sales';

export interface OrderKindConfig {
  // API base path / مسار الـ API الأساسي
  api: string;
  // Front-end route / مسار الواجهة
  route: string;
  // Partner endpoint and the field names on the order / رابط الطرف الآخر وأسماء الحقول في الطلب
  partnerApi: string;
  partnerIdField: 'supplierId' | 'customerId';
  partnerNameField: 'supplierName' | 'customerName';
  partnerLabel: string;
  // Which price the line uses by default / السعر الافتراضي للسطر
  priceField: 'costPrice' | 'salePrice';
  priceLabel: string;
  // The "complete" action: receive (purchase) or confirm (sales) / إجراء الإكمال: استلام (شراء) أو تأكيد (بيع)
  completeAction: 'receive' | 'confirm';
  completeLabel: string;
  completeConfirm: string;
  completeDone: string;
  completedAtField: 'receivedAt' | 'confirmedAt';
  completedAtLabel: string;
  title: string;
  subtitle: string;
  newLabel: string;
  statuses: string[];
}

export const ORDER_KINDS: Record<OrderKind, OrderKindConfig> = {
  purchase: {
    api: '/purchase-orders',
    route: '/purchases',
    partnerApi: '/suppliers',
    partnerIdField: 'supplierId',
    partnerNameField: 'supplierName',
    partnerLabel: 'orders.supplier',
    priceField: 'costPrice',
    priceLabel: 'orders.unitCost',
    completeAction: 'receive',
    completeLabel: 'orders.receive',
    completeConfirm: 'orders.receiveConfirm',
    completeDone: 'orders.received',
    completedAtField: 'receivedAt',
    completedAtLabel: 'orders.receivedAt',
    title: 'orders.purchasesTitle',
    subtitle: 'orders.purchasesSubtitle',
    newLabel: 'orders.newPurchase',
    statuses: ['DRAFT', 'RECEIVED', 'CANCELLED'],
  },
  sales: {
    api: '/sales-orders',
    route: '/sales',
    partnerApi: '/customers',
    partnerIdField: 'customerId',
    partnerNameField: 'customerName',
    partnerLabel: 'orders.customer',
    priceField: 'salePrice',
    priceLabel: 'orders.unitPrice',
    completeAction: 'confirm',
    completeLabel: 'orders.confirm',
    completeConfirm: 'orders.confirmConfirm',
    completeDone: 'orders.confirmed',
    completedAtField: 'confirmedAt',
    completedAtLabel: 'orders.confirmedAt',
    title: 'orders.salesTitle',
    subtitle: 'orders.salesSubtitle',
    newLabel: 'orders.newSales',
    statuses: ['DRAFT', 'CONFIRMED', 'CANCELLED'],
  },
};
