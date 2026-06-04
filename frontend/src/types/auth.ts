export type UserRole = "ROLE_USER" | "ROLE_CREATOR" | "ROLE_ADMIN";

export interface User {
  id: string;
  username?: string;
  email: string;
  fullName: string;
  avatarUrl?: string;
  roles?: UserRole[];
  status?: string;
  oauthProvider?: string;
  createdAt?: string;
}

export interface AuthResponse {
  accessToken: string;
  refreshToken: string;
  user: User;
}

export interface LoginRequest {
  usernameOrEmail: string;
  password?: string;
}

export interface RegisterRequest {
  username: string;
  email: string;
  password?: string;
  fullName: string;
  role?: string;
}

export interface Profile {
  id: string;
  user: User;
  bio?: string;
  followerCount: number;
  followingCount: number;
  isFollowing?: boolean;
}
