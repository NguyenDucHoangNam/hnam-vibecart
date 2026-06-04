import { api } from "@/lib/api-client";
import { ENDPOINTS } from "@/constants/api";
import { Product } from "@/types";

export interface SearchProductsParams {
  query?: string;
  category?: string;
  minPrice?: number;
  maxPrice?: number;
  minRating?: number;
  page?: number;
  size?: number;
  sortBy?: string;
}

export const productService = {
  getProducts: async (params?: SearchProductsParams) => {
    const cleanParams: Record<string, string | number | boolean> = {};
    if (params) {
      Object.entries(params).forEach(([key, val]) => {
        if (val !== undefined && val !== null && val !== "") {
          cleanParams[key] = val;
        }
      });
    }
    return api.get<{ content: any[]; totalElements: number; suggestion?: string | null; totalPages?: number }>(ENDPOINTS.PRODUCTS.LIST, {
      params: cleanParams,
    });
  },

  getProductById: async (id: string) => {
    return api.get<Product>(ENDPOINTS.PRODUCTS.DETAILS(id));
  },

  createProduct: async (productData: Omit<Product, "id" | "createdAt" | "ratingAverage" | "ratingCount">) => {
    return api.post<Product>(ENDPOINTS.PRODUCTS.CREATE, productData);
  },

  updateProduct: async (id: string, productData: Partial<Product>) => {
    return api.put<Product>(ENDPOINTS.PRODUCTS.UPDATE(id), productData);
  },

  deleteProduct: async (id: string) => {
    return api.delete<void>(ENDPOINTS.PRODUCTS.DELETE(id));
  },

  adjustInventory: async (id: string, adjustmentQuantity: number, reason?: string) => {
    return api.post<void>(`/products/variants/${id}/inventory/adjust`, {
      adjustmentQuantity,
      reason,
    });
  },

  getProductsByCreator: async (creatorId: string, params?: { page?: number; size?: number }) => {
    return api.get<{ content: Product[]; totalElements: number }>(ENDPOINTS.PRODUCTS.CREATOR(creatorId), {
      params: params as Record<string, string | number | boolean>,
    });
  },
};
