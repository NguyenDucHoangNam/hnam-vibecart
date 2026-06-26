import { API_BASE_URL } from "@/constants/api";

interface RequestOptions extends RequestInit {
  params?: Record<string, string | number | boolean>;
}

class ApiError extends Error {
  status: number;
  data: any;

  constructor(message: string, status: number, data: any) {
    super(message);
    this.name = "ApiError";
    this.status = status;
    this.data = data;
  }
}

let isRefreshing = false;
let refreshSubscribers: { resolve: (token: string) => void; reject: (err: Error) => void }[] = [];

function subscribeTokenRefresh(resolve: (token: string) => void, reject: (err: Error) => void) {
  refreshSubscribers.push({ resolve, reject });
}

function onRefreshed(token: string) {
  refreshSubscribers.forEach((sub) => sub.resolve(token));
  refreshSubscribers = [];
}

function onRefreshFailed(error: Error) {
  refreshSubscribers.forEach((sub) => sub.reject(error));
  refreshSubscribers = [];
}

async function refreshAccessToken(): Promise<string | null> {
  const refreshToken = typeof window !== "undefined" ? localStorage.getItem("refresh_token") : null;
  if (!refreshToken) return null;

  try {
    const res = await fetch(`${API_BASE_URL}/auth/refresh`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
      },
      body: JSON.stringify({ refreshToken: refreshToken }),
    });

    if (!res.ok) {
      throw new Error("Refresh token expired");
    }

    const data = await res.json();
    const newAccessToken = data.result?.accessToken;
    const newRefreshToken = data.result?.refreshToken;

    if (typeof window !== "undefined") {
      localStorage.setItem("access_token", newAccessToken);
      if (newRefreshToken) {
        localStorage.setItem("refresh_token", newRefreshToken);
      }
    }

    return newAccessToken;
  } catch (err) {
    console.error("Token refresh failed:", err);
    if (typeof window !== "undefined") {
      localStorage.removeItem("access_token");
      localStorage.removeItem("refresh_token");
      window.dispatchEvent(new Event("auth_logout"));
    }
    onRefreshFailed(new Error("Token refresh failed"));
    return null;
  }
}

export async function apiClient<T>(endpoint: string, options: RequestOptions = {}): Promise<T> {
  const { params, headers, ...customConfig } = options;
  
  let url = `${API_BASE_URL}${endpoint}`;
  if (params) {
    const query = new URLSearchParams(
      Object.entries(params).reduce((acc, [key, val]) => {
        acc[key] = String(val);
        return acc;
      }, {} as Record<string, string>)
    ).toString();
    url = `${url}?${query}`;
  }

  const token = typeof window !== "undefined" ? localStorage.getItem("access_token") : null;

  const defaultHeaders: HeadersInit = {};
  if (!(customConfig.body instanceof FormData)) {
    defaultHeaders["Content-Type"] = "application/json";
  }

  if (token) {
    defaultHeaders["Authorization"] = `Bearer ${token}`;
  }

  const config: RequestInit = {
    ...customConfig,
    headers: {
      ...defaultHeaders,
      ...headers,
    },
  };

  try {
    const response = await fetch(url, config);

    if (response.status === 401 && token) {
      if (!isRefreshing) {
        isRefreshing = true;
        try {
          const newAccessToken = await refreshAccessToken();
          isRefreshing = false;

          if (newAccessToken) {
            onRefreshed(newAccessToken);
          } else {
            onRefreshFailed(new Error("Session expired"));
            throw new ApiError("Session expired", 401, null);
          }
        } catch (refreshError) {
          isRefreshing = false;
          onRefreshFailed(refreshError instanceof Error ? refreshError : new Error("Refresh failed"));
          throw refreshError instanceof ApiError ? refreshError : new ApiError("Session expired", 401, null);
        }
      }

      return new Promise<T>((resolve, reject) => {
        subscribeTokenRefresh(
          (newToken) => {
            const retriedHeaders = {
              ...config.headers,
              "Authorization": `Bearer ${newToken}`,
            };
            fetch(url, { ...config, headers: retriedHeaders })
              .then(async (res) => {
                if (!res.ok) {
                  const errData = await res.json().catch(() => null);
                  reject(new ApiError(res.statusText || "Request failed", res.status, errData));
                } else {
                  const retryData = await res.json();
                  const unwrapped = retryData && typeof retryData === "object" && "result" in retryData ? retryData.result : retryData;
                  resolve(unwrapped as T);
                }
              })
              .catch(reject);
          },
          reject
        );
      });
    }

    if (!response.ok) {
      const errorData = await response.json().catch(() => null);
      throw new ApiError(
        errorData?.message || response.statusText || "Request failed",
        response.status,
        errorData
      );
    }

    if (response.status === 204) {
      return {} as T;
    }

    const jsonData = await response.json();
    
    if (jsonData && typeof jsonData === "object" && "result" in jsonData) {
      return jsonData.result as T;
    }
    
    return jsonData as T;
  } catch (error) {
    if (error instanceof ApiError) {
      throw error;
    }
    throw new ApiError((error as Error).message || "Network error", 500, null);
  }
}

export const api = {
  get: <T>(endpoint: string, options?: RequestOptions) => 
    apiClient<T>(endpoint, { ...options, method: "GET" }),
    
  post: <T>(endpoint: string, data?: any, options?: RequestOptions) => 
    apiClient<T>(endpoint, { 
      ...options, 
      method: "POST", 
      body: data instanceof FormData ? data : (data ? JSON.stringify(data) : undefined) 
    }),
    
  put: <T>(endpoint: string, data?: any, options?: RequestOptions) => 
    apiClient<T>(endpoint, { 
      ...options, 
      method: "PUT", 
      body: data instanceof FormData ? data : (data ? JSON.stringify(data) : undefined) 
    }),
    
  delete: <T>(endpoint: string, options?: RequestOptions) => 
    apiClient<T>(endpoint, { ...options, method: "DELETE" }),
};
