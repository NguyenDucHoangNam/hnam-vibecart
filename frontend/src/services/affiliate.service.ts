import { api } from "@/lib/api-client";
import { ENDPOINTS } from "@/constants/api";
import { ShortLink, CreatorSummary } from "@/types";

export const affiliateService = {
  generateShortLink: async (productId: string, originalUrl: string) => {
    return api.post<ShortLink>(ENDPOINTS.AFFILIATE.GENERATE_SHORT, {
      productId,
      originalUrl,
    });
  },

  getCreatorSummary: async () => {
    return api.get<CreatorSummary>(ENDPOINTS.AFFILIATE.SUMMARY);
  },

  getAnalytics: async (startDate?: string, endDate?: string) => {
    const params: Record<string, string> = {};
    if (startDate) params.startDate = startDate;
    if (endDate) params.endDate = endDate;
    
    return api.get<CreatorSummary["recentAnalytics"]>(ENDPOINTS.AFFILIATE.ANALYTICS, { params });
  },
};
