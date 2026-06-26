import { api } from "@/lib/api-client";
import { ENDPOINTS } from "@/constants/api";
import { SearchResultResponse, SearchHistoryResponse, SearchMergeItem, UserSearchResultResponse } from "@/types";

export interface SearchParams {
  q?: string;
  categoryId?: string;
  minPrice?: number;
  maxPrice?: number;
  sort?: string;
  page?: number;
  size?: number;
}

export interface UserSearchParams {
  q?: string;
  page?: number;
  size?: number;
}

export const searchService = {
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

  getAutocomplete: async (prefix: string) => {
    if (!prefix.trim()) return [];
    const res = await api.get<string[]>(ENDPOINTS.SEARCH.AUTOCOMPLETE, {
      params: { prefix }
    });
    return res;
  },

  getUserAutocomplete: async (prefix: string) => {
    if (!prefix.trim()) return [];
    const res = await api.get<string[]>(ENDPOINTS.SEARCH.USERS_AUTOCOMPLETE, {
      params: { prefix }
    });
    return res;
  },

  getTrending: async () => {
    const res = await api.get<string[]>(ENDPOINTS.SEARCH.TRENDING);
    return res;
  },

  getHistory: async () => {
    const res = await api.get<SearchHistoryResponse[]>(ENDPOINTS.SEARCH.HISTORY);
    return res;
  },

  deleteHistoryKeyword: async (keyword: string) => {
    const res = await api.delete<void>(ENDPOINTS.SEARCH.HISTORY, {
      params: { keyword }
    });
    return res;
  },

  clearHistory: async () => {
    const res = await api.delete<void>(ENDPOINTS.SEARCH.CLEAR_HISTORY);
    return res;
  },

  mergeHistory: async (keywords: SearchMergeItem[]) => {
    const res = await api.post<void>(ENDPOINTS.SEARCH.MERGE_HISTORY, {
      keywords
    });
    return res;
  },

  reindexAll: async () => {
    const res = await api.post<void>(ENDPOINTS.SEARCH.SYNC);
    return res;
  }
};
export default searchService;
