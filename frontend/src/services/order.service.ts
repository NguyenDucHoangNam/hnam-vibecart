import { api } from "@/lib/api-client";
import { ENDPOINTS } from "@/constants/api";
import { Order, CheckoutResponse } from "@/types";

export interface PlaceOrderPayload {
  items: {
    variantId: string;
    quantity: number;
  }[];
  shippingAddress: string;
  recipientName: string;
  recipientPhone: string;
  customerNote?: string;
  voucherCode?: string;
}

export const orderService = {
  placeOrder: async (payload: PlaceOrderPayload, idempotencyKey?: string) => {
    const headers = idempotencyKey ? { "X-Idempotency-Key": idempotencyKey } : undefined;
    return api.post<CheckoutResponse>(ENDPOINTS.ORDERS.CREATE, payload, { headers });
  },

  getOrderDetails: async (orderId: string) => {
    return api.get<Order>(ENDPOINTS.ORDERS.DETAILS(orderId));
  },

  getMyOrders: async (status?: string, page = 0, size = 10) => {
    return api.get<{ content: Order[]; totalElements: number }>(ENDPOINTS.ORDERS.LIST, {
      params: {
        ...(status ? { status } : {}),
        page,
        size,
      },
    });
  },

  cancelOrder: async (orderId: string) => {
    return api.post<Order>(ENDPOINTS.ORDERS.CANCEL(orderId), {});
  },

  getCreatorOrders: async (status?: string, page = 0, size = 10) => {
    return api.get<{ content: Order[]; totalElements: number }>(`${ENDPOINTS.ORDERS.LIST}/creator`, {
      params: {
        ...(status ? { status } : {}),
        page,
        size,
      },
    });
  },

  updateOrderStatus: async (orderId: string, newStatus: string, trackingNumber?: string) => {
    return api.put<Order>(`${ENDPOINTS.ORDERS.LIST}/${orderId}/status`, {
      newStatus,
      trackingNumber,
    });
  },
};
