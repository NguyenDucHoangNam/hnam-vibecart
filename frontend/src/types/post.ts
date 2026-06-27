export interface Comment {
  id: string;
  postId: string;
  userId: string;
  username: string;
  userAvatarUrl?: string;
  content: string;
  parentId?: string;
  replies?: Comment[];
  createdAt: string;
  updatedAt?: string;
}

export interface Post {
  id: string;
  creatorId: string;
  creatorUsername: string;
  creatorFullName?: string;
  creatorAvatarUrl?: string;
  content: string;
  mediaUrls?: string[];
  taggedProductIds?: string[];
  likeCount: number;
  commentCount: number;
  likedByMe: boolean;
  visibility: "PUBLIC" | "FOLLOWERS" | "PRIVATE";
  createdAt: string;
  updatedAt?: string;
}
