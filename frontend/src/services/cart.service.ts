import { api } from "@/lib/api-client";
import { ENDPOINTS } from "@/constants/api";
import { Cart } from "@/types";

export interface CartMergeItem {
  variantId: string;
  quantity: number;
}

export const cartService = {
  getCart: async () => {
    return api.get<Cart>(ENDPOINTS.CART.GET);
  },

  addItem: async (variantId: string, quantity: number) => {
    return api.post<void>(ENDPOINTS.CART.ADD, {
      variantId,
      quantity,
    });
  },

  updateQuantity: async (variantId: string, quantity: number) => {
    return api.put<void>(ENDPOINTS.CART.UPDATE_QTY(variantId), {
      quantity,
    });
  },

  removeItem: async (variantId: string) => {
    return api.delete<void>(ENDPOINTS.CART.REMOVE(variantId));
  },

  mergeCart: async (items: CartMergeItem[]) => {
    return api.post<void>(`${ENDPOINTS.CART.GET}/merge`, {
      items,
    });
  },

  clearCart: async () => {
    return api.delete<void>(ENDPOINTS.CART.CLEAR);
  },
};
