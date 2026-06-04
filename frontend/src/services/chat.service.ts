import { api } from "@/lib/api-client";
import { ENDPOINTS } from "@/constants/api";
import { PageResponse } from "./post.service";
import { 
  ConversationResponse, 
  MessageResponse, 
  PresenceResponse, 
  PresignedUrlResponse 
} from "@/types";

export interface PresignedUrlRequest {
  fileName: string;
  fileSize: number;
  contentType: string;
}

export const chatService = {
  // Lấy danh sách cuộc hội thoại
  getConversations: async () => {
    const res = await api.get<ConversationResponse[]>(ENDPOINTS.CHAT.CONVERSATIONS);
    return res;
  },

  // Tạo hoặc lấy phòng chat Direct/Group
  createConversation: async (memberIds: string[], type: "DIRECT" | "GROUP", name?: string) => {
    const res = await api.post<ConversationResponse>(ENDPOINTS.CHAT.CONVERSATIONS, {
      memberIds,
      type,
      name
    });
    return res;
  },

  // Lấy lịch sử tin nhắn của một phòng (Phân trang)
  getMessages: async (conversationId: string, page = 0, size = 30) => {
    const res = await api.get<PageResponse<MessageResponse>>(
      ENDPOINTS.CHAT.MESSAGES(conversationId),
      { params: { page, size } }
    );
    return res;
  },

  // Lấy S3/Storage pre-signed upload URL
  getPresignedUrl: async (payload: PresignedUrlRequest) => {
    const res = await api.post<PresignedUrlResponse>(
      ENDPOINTS.CHAT.ATTACHMENT_URL,
      payload
    );
    return res;
  },

  // Lấy trạng thái trực tuyến của user
  getUserPresence: async (userId: string) => {
    const res = await api.get<PresenceResponse>(
      ENDPOINTS.CHAT.PRESENCE(userId)
    );
    return res;
  },

  // Upload tệp trực tiếp lên S3/Storage thông qua pre-signed PUT url
  uploadAttachmentFile: async (
    uploadUrl: string,
    file: File,
    onProgress?: (progressPercent: number) => void
  ): Promise<void> => {
    return new Promise((resolve, reject) => {
      const xhr = new XMLHttpRequest();
      xhr.open("PUT", uploadUrl, true);
      xhr.setRequestHeader("Content-Type", file.type);

      if (xhr.upload && onProgress) {
        xhr.upload.onprogress = (event) => {
          if (event.lengthComputable) {
            const percentComplete = Math.round((event.loaded / event.total) * 100);
            onProgress(percentComplete);
          }
        };
      }

      xhr.onload = () => {
        if (xhr.status === 200) {
          resolve();
        } else {
          reject(new Error(`Upload failed with status: ${xhr.status}`));
        }
      };

      xhr.onerror = () => {
        reject(new Error("Network error during file upload to storage."));
      };

      xhr.send(file);
    });
  }
};
