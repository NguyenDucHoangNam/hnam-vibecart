export interface ProductImage {
  id: string;
  imageUrl: string;
  isThumbnail: boolean;
  sortOrder: number;
}

export interface ProductVariant {
  id: string;
  skuCode: string;
  variantName: string;
  price: number;
  discountPrice: number;
  quantity: number;
  reservedQuantity: number;
  availableStock: number;
  status: "ACTIVE" | "INACTIVE";
}

export interface Product {
  id: string;
  name: string;
  description: string;
  categoryId: string;
  categoryName: string;
  creatorId: string;
  creatorName?: string;
  status: "ACTIVE" | "INACTIVE";
  images: ProductImage[];
  variants: ProductVariant[];
  createdAt: string;
  updatedAt: string;
  imageUrl?: string;
  price?: number;
  category?: string;
}

export interface CartItem {
  variantId: string;
  spuId: string;
  productName: string;
  variantName: string;
  thumbnailUrl: string;
  creatorId: string;
  creatorName: string;
  quantity: number;
  originalPrice: number;
  discountPrice: number;
  availableStock: number;
  status: "AVAILABLE" | "OUT_OF_STOCK" | "INSUFFICIENT_STOCK";
}

export interface Cart {
  items: CartItem[];
  totalOriginalAmount: number;
  totalDiscountAmount: number;
  totalSavingAmount: number;
}

export type OrderStatus = "PENDING" | "PAID" | "SHIPPED" | "DELIVERED" | "CANCELLED";

export interface OrderItem {
  variantId: string;
  productName: string;
  variantName: string;
  price: number;
  discountPrice: number;
  quantity: number;
}

export interface Order {
  orderId: string;
  orderCode: string;
  creatorId: string;
  creatorName?: string;
  recipientName: string;
  recipientPhone: string;
  shippingAddress: string;
  items: OrderItem[];
  totalAmount: number;
  discountAmount: number;
  finalAmount: number;
  status: OrderStatus;
  paymentUrl?: string;
  customerNote?: string;
  trackingNumber?: string;
  createdAt: string;
}

export interface CheckoutResponse {
  checkoutSessionId: string;
  subOrders: Order[];
  createdAt: string;
}

export interface InventoryHistoryResponse {
  id: string;
  transactionType: string;
  quantityChanged: number;
  reason: string;
  createdBy: string;
  createdAt: string;
}

export interface Category {
  id: string;
  name: string;
  slug: string;
  parentId?: string;
  children?: Category[];
}
