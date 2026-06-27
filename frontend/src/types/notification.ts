export interface NotificationActorInfo {
  id: string;
  username: string;
  fullName: string;
  avatarUrl?: string;
}

export interface NotificationResponse {
  id: string;
  type: "FOLLOW" | "LIKE" | "COMMENT" | "ORDER_PAID" | "ORDER_DELIVERED" | "PRODUCT_NEW";
  content: string;
  read: boolean;
  createdAt: string;
  actor: NotificationActorInfo;
  referenceId?: string;
  sendSound?: boolean;
}
