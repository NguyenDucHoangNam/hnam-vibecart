import { api } from "@/lib/api-client";
import { ENDPOINTS } from "@/constants/api";
import { SearchResultResponse, SearchHistoryResponse, SearchMergeItem, UserSearchResultResponse } from "@/types";

export interface SearchParams {
  q?: string;
  categoryId?: string;
  minPrice?: number;
  maxPrice?: number;
  sort?: string; // relevance, price_asc, price_desc, newest, popular
  page?: number;
  size?: number;
}

export interface UserSearchParams {
  q?: string;
  page?: number;
  size?: number;
}

export const searchService = {
  // 1. Tìm kiếm và phân trang kết quả sản phẩm từ Elasticsearch
  search: async (params: SearchParams) => {
    const queryParams: Record<string, string | number | boolean> = {};
    
    if (params.q) queryParams.q = params.q;
    if (params.categoryId) queryParams.categoryId = params.categoryId;
    if (params.minPrice !== undefined) queryParams.minPrice = params.minPrice;
    if (params.maxPrice !== undefined) queryParams.maxPrice = params.maxPrice;
    
    queryParams.sort = params.sort || "relevance";
    queryParams.page = params.page ?? 0;
    queryParams.size = params.size ?? 20;

    const res = await api.get<SearchResultResponse>(ENDPOINTS.SEARCH.RESULTS, {
      params: queryParams
    });
    return res;
  },

  // 1b. Tìm kiếm và phân trang kết quả thành viên từ Elasticsearch
  searchUsers: async (params: UserSearchParams) => {
    const queryParams: Record<string, string | number | boolean> = {};
    
    if (params.q) queryParams.q = params.q;
    queryParams.page = params.page ?? 0;
    queryParams.size = params.size ?? 20;

    const res = await api.get<UserSearchResultResponse>(ENDPOINTS.SEARCH.USERS_RESULTS, {
      params: queryParams
    });
    return res;
  },

  // 2. Gợi ý từ khóa autocomplete thời gian thực
  getAutocomplete: async (prefix: string) => {
    if (!prefix.trim()) return [];
    const res = await api.get<string[]>(ENDPOINTS.SEARCH.AUTOCOMPLETE, {
      params: { prefix }
    });
    return res;
  },

  // 2b. Gợi ý từ khóa thành viên autocomplete thời gian thực
  getUserAutocomplete: async (prefix: string) => {
    if (!prefix.trim()) return [];
    const res = await api.get<string[]>(ENDPOINTS.SEARCH.USERS_AUTOCOMPLETE, {
      params: { prefix }
    });
    return res;
  },

  // 3. Lấy top các từ khóa xu hướng từ Redis
  getTrending: async () => {
    const res = await api.get<string[]>(ENDPOINTS.SEARCH.TRENDING);
    return res;
  },

  // 4. Lấy lịch sử tìm kiếm cá nhân từ MongoDB
  getHistory: async () => {
    const res = await api.get<SearchHistoryResponse[]>(ENDPOINTS.SEARCH.HISTORY);
    return res;
  },

  // 5. Xóa một từ khóa cụ thể trong lịch sử
  deleteHistoryKeyword: async (keyword: string) => {
    const res = await api.delete<void>(ENDPOINTS.SEARCH.HISTORY, {
      params: { keyword }
    });
    return res;
  },

  // 6. Xóa sạch lịch sử tìm kiếm
  clearHistory: async () => {
    const res = await api.delete<void>(ENDPOINTS.SEARCH.CLEAR_HISTORY);
    return res;
  },

  // 7. Đồng bộ LocalStorage ngoại tuyến lên MongoDB
  mergeHistory: async (keywords: SearchMergeItem[]) => {
    const res = await api.post<void>(ENDPOINTS.SEARCH.MERGE_HISTORY, {
      keywords
    });
    return res;
  },

  // 8. Quản trị viên reindex thủ công chỉ mục
  reindexAll: async () => {
    const res = await api.post<void>(ENDPOINTS.SEARCH.SYNC);
    return res;
  }
};
export default searchService;
