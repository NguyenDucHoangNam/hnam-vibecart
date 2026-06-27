import { api } from "@/lib/api-client";
import { ENDPOINTS } from "@/constants/api";
import { Post, Comment } from "@/types";

export interface PageResponse<T> {
  content: T[];
  pageNumber?: number;
  pageSize?: number;
  totalElements: number;
  totalPages: number;
  last: boolean;
}

export const postService = {
  getFeed: async (page = 0, size = 20) => {
    return api.get<PageResponse<Post>>(ENDPOINTS.POSTS.FEED, {
      params: { page, size },
    });
  },

  getPosts: async (page = 0, size = 20, creatorId?: string) => {
    const params: Record<string, string | number | boolean> = { page, size };
    if (creatorId) {
      params.creatorId = creatorId;
    }
    return api.get<PageResponse<Post>>(ENDPOINTS.POSTS.LIST, { params });
  },

  createPost: async (postData: { content: string; mediaUrls?: string[]; taggedProductIds?: string[]; visibility?: "PUBLIC" | "FOLLOWERS" | "PRIVATE" }) => {
    return api.post<Post>(ENDPOINTS.POSTS.CREATE, postData);
  },

  getPostById: async (id: string) => {
    return api.get<Post>(ENDPOINTS.POSTS.DETAILS(id));
  },

  updatePost: async (id: string, postData: { content: string; mediaUrls?: string[]; taggedProductIds?: string[]; visibility?: "PUBLIC" | "FOLLOWERS" | "PRIVATE" }) => {
    return api.put<Post>(ENDPOINTS.POSTS.DETAILS(id), postData);
  },

  deletePost: async (id: string) => {
    return api.delete<void>(ENDPOINTS.POSTS.DETAILS(id));
  },

  toggleLike: async (id: string) => {
    return api.post<boolean>(ENDPOINTS.POSTS.TOGGLE_LIKE(id));
  },

  checkLiked: async (id: string) => {
    return api.get<boolean>(ENDPOINTS.POSTS.CHECK_LIKED(id));
  },

  getLikeCount: async (id: string) => {
    return api.get<number>(ENDPOINTS.POSTS.LIKE_COUNT(id));
  },

  getComments: async (postId: string, page = 0, size = 20) => {
    return api.get<PageResponse<Comment>>(ENDPOINTS.POSTS.COMMENTS(postId), {
      params: { page, size },
    });
  },

  addComment: async (postId: string, content: string, parentId?: string) => {
    return api.post<Comment>(ENDPOINTS.POSTS.ADD_COMMENT(postId), { content, parentId });
  },

  deleteComment: async (postId: string, commentId: string) => {
    return api.delete<void>(ENDPOINTS.POSTS.DELETE_COMMENT(postId, commentId));
  },
};
