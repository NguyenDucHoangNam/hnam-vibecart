import { api } from "@/lib/api-client";
import { Category } from "@/types";

export const categoryService = {
  getCategoriesTree: async () => {
    return api.get<Category[]>("/categories");
  },
  createCategory: async (data: { name: string; parentId?: string | null }) => {
    return api.post<Category>("/categories", data);
  },
  updateCategory: async (id: string, data: { name: string; parentId?: string | null }) => {
    return api.put<Category>(`/categories/${id}`, data);
  },
  deleteCategory: async (id: string) => {
    return api.delete<void>(`/categories/${id}`);
  },
};

