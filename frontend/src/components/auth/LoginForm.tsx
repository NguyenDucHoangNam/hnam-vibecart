"use client";

import React, { useState, useEffect } from "react";
import { useAuth } from "@/context/AuthContext";
import { useToast } from "@/context/ToastContext";
import { useRouter } from "next/navigation";
import { ROUTES } from "@/constants/routes";
import { Mail, Lock, ArrowRight, Eye, EyeOff, Loader2, Info } from "lucide-react";
import Link from "next/link";
import { GoogleLogin } from "@react-oauth/google";
import Script from "next/script";

interface ApiErrorType {
  message?: string;
  status?: number;
  data?: {
    code?: number;
    message?: string;
  };
}

// Minimal types for Facebook SDK to avoid 'any'
interface FBLoginResponse {
  authResponse?: {
    accessToken: string;
  };
}

interface FBType {
  init(params: { appId: string; cookie: boolean; xfbml: boolean; version: string }): void;
  login(callback: (response: FBLoginResponse) => void, options: { scope: string }): void;
}

declare global {
  interface Window {
    FB?: FBType;
    fbAsyncInit?: () => void;
  }
}

const GoogleIcon = () => (
  <svg className="w-4 h-4 shrink-0" viewBox="0 0 24 24">
    <path d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92c-.26 1.37-1.04 2.53-2.21 3.31v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.09z" fill="#4285F4"/>
    <path d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z" fill="#34A853"/>
    <path d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.06H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.94l2.85-2.22.81-.63z" fill="#FBBC05"/>
    <path d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.06l3.66 2.84c.87-2.6 3.3-4.52 6.16-4.52z" fill="#EA4335"/>
  </svg>
);

const FacebookIcon = () => (
  <svg className="w-4 h-4 shrink-0 fill-current text-[#1877F2]" viewBox="0 0 24 24">
    <path d="M24 12.073c0-6.627-5.373-12-12-12s-12 5.373-12 12c0 5.99 4.388 10.954 10.125 11.854v-8.385H7.078v-3.47h3.047V9.43c0-3.007 1.792-4.669 4.533-4.669 1.312 0 2.686.235 2.686.235v2.953H15.83c-1.491 0-1.956.925-1.956 1.874v2.25h3.328l-.532 3.47h-2.796v8.385C19.612 23.027 24 18.062 24 12.073z" />
  </svg>
);

export default function LoginForm() {
  const { login, loginGoogle, loginFacebook } = useAuth();
  const toast = useToast();
  const router = useRouter();

  const [usernameOrEmail, setUsernameOrEmail] = useState("");
  const [password, setPassword] = useState("");
  const [showPassword, setShowPassword] = useState(false);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // Environment variables checked at runtime
  const googleClientId = process.env.NEXT_PUBLIC_GOOGLE_CLIENT_ID;
  const facebookAppId = process.env.NEXT_PUBLIC_FACEBOOK_APP_ID;

  useEffect(() => {
    // Bắt token trả về từ Facebook OAuth Redirect Flow (nằm ở hash fragment)
    const hash = window.location.hash;
    if (hash && hash.includes("access_token=")) {
      const params = new URLSearchParams(hash.substring(1));
      const accessToken = params.get("access_token");
      const state = params.get("state");

      if (accessToken && state === "facebook_login") {
        // Xóa hash trên URL để giữ thanh địa chỉ sạch sẽ
        window.history.replaceState(null, "", window.location.pathname);
        
        setError(null);
        setIsLoading(true);
        loginFacebook(accessToken)
          .then(() => {
            toast.success("Đăng nhập bằng Facebook thành công", "Chào mừng bạn quay trở lại!");
            router.push(ROUTES.HOME);
          })
          .catch((err: unknown) => {
            console.error("Facebook login backend error:", err);
            const apiErr = err as ApiErrorType;
            setError(apiErr.data?.message || "Đăng nhập bằng Facebook thất bại ở hệ thống.");
            toast.error("Đăng nhập thất bại", "Đăng nhập bằng Facebook thất bại ở hệ thống.");
          })
          .finally(() => {
            setIsLoading(false);
          });
      }
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [router, loginFacebook]);


  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    setIsLoading(true);

    try {
      await login({ usernameOrEmail, password });
      toast.success("Đăng nhập thành công", "Chào mừng bạn quay trở lại!");
      router.push(ROUTES.HOME);
    } catch (err: unknown) {
      console.error("Login error:", err);
      const apiErr = err as ApiErrorType;
      const errorMsg = apiErr.data?.message || apiErr.message || "Đăng nhập thất bại. Vui lòng kiểm tra lại tài khoản hoặc mật khẩu.";
      setError(errorMsg);
      toast.error("Đăng nhập thất bại", errorMsg);
    } finally {
      setIsLoading(false);
    }
  };

  const handleFacebookLogin = () => {
    if (!facebookAppId) {
      setError("Facebook Login chưa được cấu hình. Hãy dán APP_ID vào NEXT_PUBLIC_FACEBOOK_APP_ID trong file .env.local.");
      return;
    }

    setError(null);
    setIsLoading(true);

    const redirectUri = window.location.origin + "/login";
    const oauthUrl = `https://www.facebook.com/v18.0/dialog/oauth?client_id=${facebookAppId}&redirect_uri=${encodeURIComponent(
      redirectUri
    )}&scope=email%20public_profile&response_type=token&state=facebook_login`;

    window.location.href = oauthUrl;
  };


  return (
    <div className="w-full max-w-sm">

      {/* Container Card */}
      <div className="bg-white/80 backdrop-blur-xl border border-brand-100/40 rounded-2xl p-6 shadow-xl shadow-brand-500/5 transition-all duration-300">
        
        {/* Title Header */}
        <div className="text-center mb-6">
          <h2 className="text-2xl font-extrabold tracking-tight text-brand-800">
            Đăng nhập
          </h2>
          <p className="text-zinc-400 mt-1.5 text-sm">
            Nhập tài khoản của bạn để tiếp tục
          </p>
        </div>

        {/* General Error Display */}
        {error && (
          <div className="mb-6 p-4 bg-rose-50 dark:bg-rose-950/20 border border-rose-200 dark:border-rose-900/30 rounded-2xl flex items-start space-x-3 text-rose-700 dark:text-rose-300 text-sm leading-relaxed animate-fade-in">
            <Info className="w-5 h-5 shrink-0 mt-0.5" />
            <span>{error}</span>
          </div>
        )}

        {/* Credentials Form */}
        <form onSubmit={handleSubmit} className="space-y-4">
          {/* Email / Username Input */}
          <div className="space-y-1.5">
            <label className="text-xs font-medium text-zinc-500 uppercase tracking-wider block">
              Tài khoản hoặc Email
            </label>
            <div className="relative group">
              <span className="absolute inset-y-0 left-0 flex items-center pl-4 text-brand-300 group-focus-within:text-brand-500 transition-colors duration-200">
                <Mail className="w-5 h-5" />
              </span>
              <input
                type="text"
                required
                value={usernameOrEmail}
                onChange={(e) => setUsernameOrEmail(e.target.value)}
                placeholder="Tên đăng nhập hoặc email"
                disabled={isLoading}
                className="w-full pl-11 pr-4 py-3 bg-brand-50/40 border border-brand-100/60 rounded-xl text-sm text-zinc-800 placeholder-zinc-400 focus:outline-none focus:ring-2 focus:ring-brand-200/50 focus:border-brand-300 focus:bg-white transition-all duration-200"
              />
            </div>
          </div>

          {/* Password Input */}
          <div className="space-y-1.5">
            <div className="flex justify-between items-center">
              <label className="text-xs font-medium text-zinc-500 uppercase tracking-wider">
                Mật khẩu
              </label>
              <Link href={ROUTES.FORGOT_PASSWORD} className="text-xs font-medium text-brand-500 hover:text-brand-600 transition-colors">
                Quên mật khẩu?
              </Link>
            </div>
            <div className="relative group">
              <span className="absolute inset-y-0 left-0 flex items-center pl-4 text-brand-300 group-focus-within:text-brand-500 transition-colors duration-200">
                <Lock className="w-5 h-5" />
              </span>
              <input
                type={showPassword ? "text" : "password"}
                required
                minLength={1}
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                placeholder="Nhập mật khẩu"
                disabled={isLoading}
                className="w-full pl-11 pr-12 py-3 bg-brand-50/40 border border-brand-100/60 rounded-xl text-sm text-zinc-800 placeholder-zinc-400 focus:outline-none focus:ring-2 focus:ring-brand-200/50 focus:border-brand-300 focus:bg-white transition-all duration-200"
              />
              <button
                type="button"
                onClick={() => setShowPassword(!showPassword)}
                tabIndex={-1}
                className="absolute inset-y-0 right-0 flex items-center pr-4 text-zinc-400 hover:text-brand-500 transition-colors"
              >
                {showPassword ? <EyeOff className="w-5 h-5" /> : <Eye className="w-5 h-5" />}
              </button>
            </div>
          </div>

          {/* Submit Button */}
          <button
            type="submit"
            disabled={isLoading}
            className="w-full mt-1 py-3 bg-gradient-to-r from-brand-500 to-brand-400 hover:from-brand-600 hover:to-brand-500 text-white text-sm font-semibold rounded-xl flex items-center justify-center space-x-2 shadow-lg shadow-brand-500/20 hover:shadow-brand-500/30 active:scale-[0.98] transition-all duration-200 disabled:opacity-50 disabled:pointer-events-none cursor-pointer"
          >
            {isLoading ? (
              <Loader2 className="w-5 h-5 animate-spin" />
            ) : (
              <>
                <span>Đăng nhập</span>
                <ArrowRight className="w-5 h-5" />
              </>
            )}
          </button>
        </form>

        {/* Divider */}
        <div className="relative my-6 text-center">
          <hr className="border-brand-100/50" />
          <span className="absolute top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2 bg-white/80 backdrop-blur-sm px-3 text-[10px] font-medium text-zinc-400 uppercase tracking-wider">
            Hoặc
          </span>
        </div>

        {/* Social Authentication Buttons — single row */}
        <div className="grid grid-cols-2 gap-3 items-center">
          {googleClientId ? (
            <div className="w-full flex justify-center [&_iframe]:!w-full [&_iframe]:!min-w-full h-[40px] overflow-hidden">
              <GoogleLogin
                type="standard"
                theme="outline"
                size="large"
                shape="rectangular"
                width="100%"
                text="signin"
                onSuccess={(credentialResponse) => {
                  if (credentialResponse.credential) {
                    setError(null);
                    setIsLoading(true);
                    loginGoogle(credentialResponse.credential)
                      .then(() => {
                        toast.success("Đăng nhập bằng Google thành công", "Chào mừng bạn quay trở lại!");
                        router.push(ROUTES.HOME);
                      })
                      .catch((err: unknown) => {
                        console.error("Google login backend error:", err);
                        const apiErr = err as ApiErrorType;
                        setError(apiErr.data?.message || "Đăng nhập bằng Google thất bại.");
                        toast.error("Đăng nhập thất bại", "Đăng nhập bằng Google thất bại.");
                      })
                      .finally(() => {
                        setIsLoading(false);
                      });
                  }
                }}
                onError={() => {
                  setError("Xác thực Google thất bại.");
                }}
              />
            </div>
          ) : (
            <button
              type="button"
              onClick={() => setError("Google Login chưa được cấu hình.")}
              className="flex items-center justify-center gap-2 bg-white border border-[#dadce0] rounded-[4px] text-sm font-medium text-[#3c4043] hover:bg-[#f8f9fa] hover:border-[#d2d4d7] active:scale-[0.99] transition-all cursor-pointer w-full h-[40px] shadow-2xs"
            >
              <GoogleIcon />
              <span>Google</span>
            </button>
          )}
          <button
            type="button"
            onClick={handleFacebookLogin}
            className="flex items-center justify-center gap-2 bg-white border border-[#dadce0] rounded-[4px] text-sm font-medium text-[#3c4043] hover:bg-[#f8f9fa] hover:border-[#d2d4d7] active:scale-[0.99] transition-all cursor-pointer w-full h-[40px] shadow-2xs"
          >
            <FacebookIcon />
            <span>Facebook</span>
          </button>
        </div>

        {/* Bottom Navigation */}
        <p className="mt-6 text-center text-sm text-zinc-400">
          Chưa có tài khoản?{" "}
          <Link href={ROUTES.REGISTER} className="font-semibold text-brand-500 hover:text-brand-600 transition-colors">
            Đăng ký ngay
          </Link>
        </p>
      </div>

    </div>
  );
}

