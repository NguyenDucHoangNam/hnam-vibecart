export interface ProductDocument {
  id: string;
  name: string;
  description: string;
  categoryId: string;
  categoryName: string;
  creatorId: string;
  thumbnailUrl: string;
  minPrice: number;
  maxPrice: number;
  minOriginalPrice?: number;
  maxOriginalPrice?: number;
  status: string;
  createdAt: string;
  updatedAt: string;
}

export interface SearchResultResponse {
  content: ProductDocument[];
  suggestion?: string | null;
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  last: boolean;
}

export interface SearchHistoryResponse {
  keyword: string;
  searchedAt: string;
}

export interface SearchMergeItem {
  keyword: string;
  searchedAt: string;
}

export interface SearchMergeRequest {
  keywords: SearchMergeItem[];
}

export interface UserSearchResponse {
  id: string;
  username: string;
  email: string;
  fullName: string;
  avatarUrl: string;
  status: string;
  roles: string[];
  createdAt: string;
  isFollowing?: boolean;
  followerCount?: number;
}

export interface UserSearchResultResponse {
  content: UserSearchResponse[];
  suggestion?: string | null;
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  last: boolean;
}
