export interface ShortLink {
  id: string;
  originalUrl: string;
  shortUrl: string;
  code: string;
  productId: string;
  productName: string;
  creatorId: string;
  clickCount: number;
  createdAt: string;
}

export interface ClickRecord {
  id: string;
  linkCode: string;
  clickTime: string;
  ipAddress?: string;
  userAgent?: string;
  deviceType?: string;
  referrer?: string;
}

export interface AffiliateAnalytics {
  date: string;
  clicks: number;
  commissions: number;
  ordersCount: number;
}

export interface CreatorSummary {
  totalClicks: number;
  totalOrders: number;
  totalRevenue: number;
  totalCommissions: number;
  recentAnalytics: AffiliateAnalytics[];
  recentShortLinks: ShortLink[];
}
