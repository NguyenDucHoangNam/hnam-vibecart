"use client";

import React, { createContext, useContext, useState, useEffect } from "react";
import { User, AuthResponse, LoginRequest, RegisterRequest } from "@/types";
import { api } from "@/lib/api-client";
import { ENDPOINTS } from "@/constants/api";
import { useRouter } from "next/navigation";
import { ROUTES } from "@/constants/routes";

interface AuthContextType {
  user: User | null;
  isAuthenticated: boolean;
  isLoading: boolean;
  login: (credentials: LoginRequest) => Promise<AuthResponse>;
  register: (userData: RegisterRequest) => Promise<User>;
  verifyOtp: (email: string, otpCode: string) => Promise<AuthResponse>;
  resendOtp: (email: string) => Promise<void>;
  loginGoogle: (token: string) => Promise<AuthResponse>;
  loginFacebook: (token: string) => Promise<AuthResponse>;
  logout: () => Promise<void>;
  updateUser: (updatedUser: Partial<User>) => void;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [user, setUser] = useState<User | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const router = useRouter();

  // Load user profile on mount
  useEffect(() => {
    async function loadUser() {
      const token = localStorage.getItem("access_token");
      if (!token) {
        setIsLoading(false);
        return;
      }

      try {
        const userData = await api.get<User>(ENDPOINTS.AUTH.ME);
        setUser(userData);
      } catch (err) {
        console.error("Lỗi nạp thông tin cá nhân /me:", err);
        // Chỉ xóa tokens khi server trả 401 Unauthorized (token hết hạn/không hợp lệ)
        // Không xóa khi lỗi mạng, CORS, hoặc server error (500) để tránh logout nhầm
        const status = err instanceof Error && 'status' in err ? (err as { status: number }).status : undefined;
        if (status === 401) {
          console.warn("Token hết hạn hoặc không hợp lệ, đăng xuất.");
          localStorage.removeItem("access_token");
          localStorage.removeItem("refresh_token");
        } else {
          console.warn("Lỗi tạm thời khi gọi /me, giữ lại token.");
        }
      } finally {
        setIsLoading(false);
      }
    }

    loadUser();

    // Listen for logout events triggered by api-client (e.g., token refresh failed)
    const handleLogoutEvent = () => {
      setUser(null);
      router.push(ROUTES.LOGIN);
    };

    window.addEventListener("auth_logout", handleLogoutEvent);
    return () => {
      window.removeEventListener("auth_logout", handleLogoutEvent);
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const login = async (credentials: LoginRequest): Promise<AuthResponse> => {
    try {
      const response = await api.post<AuthResponse>(ENDPOINTS.AUTH.LOGIN, credentials);
      
      localStorage.setItem("access_token", response.accessToken);
      localStorage.setItem("refresh_token", response.refreshToken);
      setUser(response.user);
      
      return response;
    } catch (error) {
      setUser(null);
      throw error;
    }
  };

  const register = async (userData: RegisterRequest): Promise<User> => {
    try {
      const response = await api.post<User>(ENDPOINTS.AUTH.REGISTER, userData);
      // Đăng ký thành công trả về thông tin User (status: PENDING_VERIFICATION)
      // Không ghi token vì chưa qua xác thực OTP
      return response;
    } catch (error) {
      setUser(null);
      throw error;
    }
  };

  const resendOtp = async (email: string): Promise<void> => {
    await api.post(ENDPOINTS.AUTH.RESEND_OTP, { email });
  };

  const verifyOtp = async (email: string, otpCode: string): Promise<AuthResponse> => {
    try {
      const response = await api.post<AuthResponse>(ENDPOINTS.AUTH.VERIFY_OTP, { email, otpCode });
      
      localStorage.setItem("access_token", response.accessToken);
      localStorage.setItem("refresh_token", response.refreshToken);
      setUser(response.user);
      
      return response;
    } catch (error) {
      setUser(null);
      throw error;
    }
  };

  const loginGoogle = async (token: string): Promise<AuthResponse> => {
    try {
      const response = await api.post<AuthResponse>(ENDPOINTS.AUTH.GOOGLE, { token });
      
      localStorage.setItem("access_token", response.accessToken);
      localStorage.setItem("refresh_token", response.refreshToken);
      setUser(response.user);
      
      return response;
    } catch (error) {
      setUser(null);
      throw error;
    }
  };

  const loginFacebook = async (token: string): Promise<AuthResponse> => {
    try {
      const response = await api.post<AuthResponse>(ENDPOINTS.AUTH.FACEBOOK, { token });
      
      localStorage.setItem("access_token", response.accessToken);
      localStorage.setItem("refresh_token", response.refreshToken);
      setUser(response.user);
      
      return response;
    } catch (error) {
      setUser(null);
      throw error;
    }
  };

  const logout = async (): Promise<void> => {
    try {
      const refreshToken = localStorage.getItem("refresh_token");
      if (refreshToken) {
        await api.post(ENDPOINTS.AUTH.LOGOUT, { refreshToken: refreshToken }).catch(() => {
          // Ignore logout api failure, proceed with local logout
        });
      }
    } finally {
      localStorage.removeItem("access_token");
      localStorage.removeItem("refresh_token");
      setUser(null);
      router.push(ROUTES.HOME);
    }
  };

  const updateUser = (updatedUser: Partial<User>) => {
    setUser((prev) => (prev ? { ...prev, ...updatedUser } : null));
  };

  return (
    <AuthContext.Provider
      value={{
        user,
        isAuthenticated: !!user,
        isLoading,
        login,
        register,
        verifyOtp,
        resendOtp,
        loginGoogle,
        loginFacebook,
        logout,
        updateUser,
      }}
    >
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  const context = useContext(AuthContext);
  if (context === undefined) {
    throw new Error("useAuth must be used within an AuthProvider");
  }
  return context;
}
