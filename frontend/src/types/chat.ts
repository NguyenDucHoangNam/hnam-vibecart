import { User } from "./auth";

export interface LastMessageResponse {
  messageId: string;
  senderId: string;
  content: string;
  type: "TEXT" | "IMAGE" | "VIDEO" | "DOCUMENT" | "PRODUCT" | "ORDER";
  createdAt: string;
}

export interface ConversationResponse {
  id: string;
  type: "DIRECT" | "GROUP";
  name?: string;
  avatarUrl?: string;
  memberIds: string[];
  unreadCounts: Record<string, number>;
  lastMessage?: LastMessageResponse;
  createdAt: string;
  updatedAt: string;
  members: User[];
}

export interface PresignedUrlResponse {
  uploadUrl: string;
  fileUrl: string;
  fileKey: string;
}

export interface AttachmentMetadataResponse {
  fileUrl: string;
  fileName: string;
  fileSize: number;
  mimeType: string;
  cardId?: string;
}

export interface ReadReceiptResponse {
  userId: string;
  readAt: string;
}

export interface MessageResponse {
  id: string;
  conversationId: string;
  senderId: string;
  content: string;
  type: "TEXT" | "IMAGE" | "VIDEO" | "DOCUMENT" | "PRODUCT" | "ORDER";
  attachmentMetadata?: AttachmentMetadataResponse;
  readBy: ReadReceiptResponse[];
  createdAt: string;
}

export interface TypingResponse {
  conversationId: string;
  username: string;
  isTyping: boolean;
}

export interface PresenceResponse {
  userId: string;
  status: "ONLINE" | "OFFLINE";
  lastActiveAt: string;
}

export interface ConversationRequest {
  type: "DIRECT" | "GROUP";
  name?: string;
  memberIds: string[];
}

export interface ChatRoom extends ConversationResponse {}
export interface Message extends MessageResponse {
  senderName: string;
}
