import { api } from "@/lib/api-client";
import { ENDPOINTS } from "@/constants/api";
import { PageResponse } from "./post.service";

export interface FollowResponse {
  userId: string;
  username: string;
  fullName: string;
  avatarUrl?: string;
  followedByMe: boolean;
  followedAt: string;
}

export const userService = {
  toggleFollow: async (userId: string) => {
    return api.post<boolean>(ENDPOINTS.USERS.TOGGLE_FOLLOW(userId));
  },

  checkFollow: async (userId: string) => {
    return api.get<boolean>(ENDPOINTS.USERS.CHECK_FOLLOW(userId));
  },

  getFollowers: async (userId: string, page = 0, size = 20) => {
    return api.get<PageResponse<FollowResponse>>(ENDPOINTS.USERS.FOLLOWERS(userId), {
      params: { page, size },
    });
  },

  getFollowing: async (userId: string, page = 0, size = 20) => {
    return api.get<PageResponse<FollowResponse>>(ENDPOINTS.USERS.FOLLOWING(userId), {
      params: { page, size },
    });
  },

  getFollowerCount: async (userId: string) => {
    return api.get<number>(ENDPOINTS.USERS.FOLLOWERS_COUNT(userId));
  },

  getFollowingCount: async (userId: string) => {
    return api.get<number>(ENDPOINTS.USERS.FOLLOWING_COUNT(userId));
  },
  
  getActiveUsers: async () => {
    return api.get<FollowResponse[]>(ENDPOINTS.CHAT.ACTIVE_PRESENCE);
  },
  getUserProfile: async (userId: string) => {
    return api.get<{
      id: string;
      username: string;
      fullName: string;
      avatarUrl?: string;
      createdAt: string;
      roles?: string[];
    }>(ENDPOINTS.USERS.PROFILE(userId));
  },
};
